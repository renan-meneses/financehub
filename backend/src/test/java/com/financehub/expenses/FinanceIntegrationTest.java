package com.financehub.expenses;

import com.financehub.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinanceIntegrationTest extends PostgresIntegrationTestSupport {
    @SpyBean com.financehub.identity.infrastructure.IdentityStore audit;
    record Setup(Login user,String workspace,String account,String category) {}
    private ResultActions request(Login user,String workspace,MockHttpServletRequestBuilder request,Object body,UUID key) throws Exception {
        request.header("Authorization","Bearer "+user.access()).header("X-Workspace-Id",workspace);
        if(key!=null) request.header("Idempotency-Key",key);
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    private JsonNode data(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private Setup setup(String balance) throws Exception {
        var user=verifiedUser();
        String workspace=data(request(user,"unused",post("/api/v1/workspaces"),Map.of("name","Finance test","kind","PERSONAL","currency","BRL","timezone","America/Fortaleza"),null).andExpect(status().isCreated())).get("id").asText();
        String account=data(request(user,workspace,post("/api/v1/accounts"),Map.of("name","Bank","institution","Test","type","CHECKING","currency","BRL","openingBalance",balance,"openingDate","2020-01-01"),UUID.randomUUID()).andExpect(status().isCreated())).get("id").asText();
        String category=data(request(user,workspace,post("/api/v1/categories"),Map.of("name","Food"),UUID.randomUUID()).andExpect(status().isCreated())).get("id").asText();
        return new Setup(user,workspace,account,category);
    }
    private Map<String,Object> draft(Setup s,String amount) { return Map.of("accountId",s.account(),"categoryId",s.category(),"description","Groceries","amount",amount,"dueDate","2024-03-10"); }
    private String debt(Setup s,String amount) throws Exception { return data(request(s.user(),s.workspace(),post("/api/v1/debts"),draft(s,amount),UUID.randomUUID()).andExpect(status().isCreated())).get("id").asText(); }
    private ResultActions pay(Setup s,String id,String date,UUID key) throws Exception { return request(s.user(),s.workspace(),post("/api/v1/debts/"+id+"/payments"),Map.of("paidDate",date),key); }
    private ResultActions analysis(Setup s) throws Exception { return request(s.user(),s.workspace(),get("/api/v1/dashboard").param("from","2024-03-01").param("to","2024-03-31"),null,null); }
    @Test void pendingPaymentAndAnalysisReconcileWithoutDoubleCounting() throws Exception {
        var s=setup("1000.00");var id=debt(s,"150.25");
        analysis(s).andExpect(status().isOk()).andExpect(jsonPath("totals[0].balance").value("1000.00"))
            .andExpect(jsonPath("totals[0].pending").value("150.25")).andExpect(jsonPath("totals[0].overdue").value("150.25"))
            .andExpect(jsonPath("totals[0].paid").value("0.00")).andExpect(jsonPath("totals[0].availableAfterPending").value("849.75"));
        var key=UUID.randomUUID();pay(s,id,"2024-03-12",key).andExpect(status().isOk()).andExpect(jsonPath("status").value("PAID"));
        pay(s,id,"2024-03-12",key).andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed","true"));
        pay(s,id,"2024-03-13",key).andExpect(status().isConflict());
        pay(s,id,"2024-03-12",UUID.randomUUID()).andExpect(status().isOk());
        analysis(s).andExpect(jsonPath("totals[0].balance").value("849.75")).andExpect(jsonPath("totals[0].pending").value("0.00"))
            .andExpect(jsonPath("totals[0].paid").value("150.25")).andExpect(jsonPath("categories[0].sharePercent").value("100.00"))
            .andExpect(jsonPath("months[0].amount").value("150.25"));
        request(s.user(),s.workspace(),get("/api/v1/accounts/"+s.account()+"/ledger"),null,null)
            .andExpect(jsonPath("total").value(2)).andExpect(jsonPath("items[1].kind").value("DEBT_PAYMENT"))
            .andExpect(jsonPath("items[1].amount").value("-150.25"));
        request(s.user(),s.workspace(),post("/api/v1/debts/"+id+"/cancellation"),Map.of(),null).andExpect(status().isConflict());
    }
    @Test void concurrentPaymentsOnlyDebitOnceAndPreventOverspending() throws Exception {
        var s=setup("100.00");var first=debt(s,"75.00");var second=debt(s,"75.00");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return pay(s,first,"2024-03-12",UUID.randomUUID()).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{gate.await();return pay(s,second,"2024-03-12",UUID.randomUUID()).andReturn().getResponse().getStatus();});
            gate.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,422);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_ledger_entries WHERE workspace_id=? AND kind='DEBT_PAYMENT'",Long.class,UUID.fromString(s.workspace()))).isEqualTo(1);
        analysis(s).andExpect(jsonPath("totals[0].balance").value("25.00")).andExpect(jsonPath("totals[0].shortfall").value(true));
    }
    @Test void retriesAndCancellationPreserveBalances() throws Exception {
        var s=setup("100.00");var key=UUID.randomUUID();
        var id=data(request(s.user(),s.workspace(),post("/api/v1/debts"),draft(s,"20.00"),key).andExpect(status().isCreated())).get("id").asText();
        request(s.user(),s.workspace(),post("/api/v1/debts"),draft(s,"20"),key).andExpect(status().isOk());
        request(s.user(),s.workspace(),post("/api/v1/debts"),draft(s,"21"),key).andExpect(status().isConflict());
        for(int i=0;i<2;i++) request(s.user(),s.workspace(),post("/api/v1/debts/"+id+"/cancellation"),Map.of(),null).andExpect(status().isOk()).andExpect(jsonPath("status").value("CANCELLED"));
        pay(s,id,"2024-03-12",UUID.randomUUID()).andExpect(status().isConflict());
        analysis(s).andExpect(jsonPath("totals[0].balance").value("100.00")).andExpect(jsonPath("totals[0].pending").value("0.00"));
    }
    @Test void validatesPrecisionDatesAndCategoryState() throws Exception {
        var s=setup("100.00");
        for(String amount:List.of("0","1.001")) request(s.user(),s.workspace(),post("/api/v1/debts"),draft(s,amount),UUID.randomUUID()).andExpect(status().isUnprocessableEntity());
        var id=debt(s,"10.00");
        pay(s,id,"2019-12-31",UUID.randomUUID()).andExpect(status().isUnprocessableEntity());
        pay(s,id,"2999-01-01",UUID.randomUUID()).andExpect(status().isUnprocessableEntity());
        request(s.user(),s.workspace(),post("/api/v1/categories"),Map.of("name","food"),UUID.randomUUID()).andExpect(status().isConflict());
        request(s.user(),s.workspace(),patch("/api/v1/categories/"+s.category()),Map.of("name","Meals","status","ARCHIVED","version",0),null).andExpect(status().isOk());
        request(s.user(),s.workspace(),patch("/api/v1/categories/"+s.category()),Map.of("name","Stale","version",0),null).andExpect(status().isConflict());
        request(s.user(),s.workspace(),post("/api/v1/debts"),draft(s,"10"),UUID.randomUUID()).andExpect(status().isUnprocessableEntity());
        pay(s,id,"2024-03-12",UUID.randomUUID()).andExpect(status().isOk());
        analysis(s).andExpect(jsonPath("categories[0].name").value("Meals"));
        request(s.user(),s.workspace(),get("/api/v1/dashboard").param("from","2024-04-01").param("to","2024-03-01"),null,null).andExpect(status().isUnprocessableEntity());
        request(s.user(),s.workspace(),get("/api/v1/dashboard").param("from","2020-01-01").param("to","2024-03-01"),null,null).andExpect(status().isUnprocessableEntity());
    }
    @Test void deniesForeignReferencesAndReadonlyMutations() throws Exception {
        var s=setup("100.00");var other=setup("50.00");var id=debt(s,"10");
        for(String resource:List.of("categories","debts","dashboard?from=2024-03-01&to=2024-03-31"))
            request(other.user(),s.workspace(),get("/api/v1/"+resource),null,null).andExpect(status().isNotFound());
        var foreign=new HashMap<>(draft(s,"10"));foreign.put("categoryId",other.category());
        request(s.user(),s.workspace(),post("/api/v1/debts"),foreign,UUID.randomUUID()).andExpect(status().isNotFound());
        foreign=new HashMap<>(draft(s,"10"));foreign.put("accountId",other.account());
        request(s.user(),s.workspace(),post("/api/v1/debts"),foreign,UUID.randomUUID()).andExpect(status().isNotFound());
        pay(other,id,"2024-03-12",UUID.randomUUID()).andExpect(status().isNotFound());
        jdbc.update("UPDATE workspace_members SET role_code='READ_ONLY' WHERE workspace_id=?",UUID.fromString(s.workspace()));
        pay(s,id,"2024-03-12",UUID.randomUUID()).andExpect(status().isForbidden());
        request(s.user(),s.workspace(),post("/api/v1/debts"),draft(s,"10"),UUID.randomUUID()).andExpect(status().isForbidden());
        request(s.user(),s.workspace(),post("/api/v1/categories"),Map.of("name","New"),UUID.randomUUID()).andExpect(status().isForbidden());
        request(s.user(),s.workspace(),patch("/api/v1/categories/"+s.category()),Map.of("name","New","version",0),null).andExpect(status().isForbidden());
        request(s.user(),s.workspace(),post("/api/v1/debts/"+id+"/cancellation"),Map.of(),null).andExpect(status().isForbidden());
        analysis(s).andExpect(status().isOk());
    }
    @Test void auditFailureRollsBackPaymentAndReceipt() throws Exception {
        var s=setup("100.00");var id=debt(s,"20");
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).audit(any(UUID.class),eq(UUID.fromString(s.workspace())),eq("DEBT_PAID"),eq("DEBT"),any(UUID.class));
        assertThatThrownBy(()->pay(s,id,"2024-03-12",UUID.randomUUID())).hasRootCauseMessage("Synthetic audit failure");
        analysis(s).andExpect(jsonPath("totals[0].balance").value("100.00")).andExpect(jsonPath("totals[0].pending").value("20.00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_requests WHERE workspace_id=? AND operation LIKE 'DEBT_PAY:%'",Long.class,UUID.fromString(s.workspace()))).isZero();
    }
    @Test void comparesEqualLengthPeriodsAndKeepsCurrenciesSeparate() throws Exception {
        var s=setup("1000.00");var old=debt(s,"100");pay(s,old,"2024-02-15",UUID.randomUUID()).andExpect(status().isOk());
        var current=debt(s,"150");pay(s,current,"2024-03-15",UUID.randomUUID()).andExpect(status().isOk());
        request(s.user(),s.workspace(),post("/api/v1/accounts"),Map.of("name","USD","institution","Test","type","CHECKING","currency","USD","openingBalance","25.00","openingDate","2020-01-01"),UUID.randomUUID()).andExpect(status().isCreated());
        analysis(s).andExpect(jsonPath("totals.length()").value(2)).andExpect(jsonPath("totals[0].currency").value("BRL"))
            .andExpect(jsonPath("totals[0].previousPaid").value("100.00")).andExpect(jsonPath("totals[0].changePercent").value("50.00"))
            .andExpect(jsonPath("totals[1].currency").value("USD")).andExpect(jsonPath("totals[1].balance").value("25.00"))
            .andExpect(jsonPath("months[1].amount").value("0.00"));
    }
}
