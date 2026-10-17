package com.financehub.financialaccounts;

import com.financehub.support.PostgresIntegrationTestSupport;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountIntegrationTest extends PostgresIntegrationTestSupport {
    @SpyBean com.financehub.identity.infrastructure.IdentityStore audit;
    private UUID workspace(Login login) throws Exception {
        var response=mvc.perform(post("/api/v1/workspaces").header("Authorization","Bearer "+login.access()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("name","Account test","kind","PERSONAL","currency","BRL","timezone","America/Fortaleza"))))
            .andExpect(status().isCreated()).andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText());
    }
    private Map<String,Object> draft(String amount) {
        return Map.of("name","Checking","institution","Synthetic bank","type","CHECKING","currency","BRL",
            "openingBalance",amount,"openingDate","2020-01-01");
    }
    private ResultActions create(Login login,UUID workspace,UUID key,Map<String,Object> draft) throws Exception {
        return mvc.perform(post("/api/v1/accounts").header("Authorization","Bearer "+login.access()).header("X-Workspace-Id",workspace)
            .header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsString(draft)));
    }
    @Test void openingBalanceIsExactAndLedgerCannotBeRewritten() throws Exception {
        var user=verifiedUser();var workspace=workspace(user);
        var response=create(user,workspace,UUID.randomUUID(),draft("123456789012345.67"))
            .andExpect(status().isCreated()).andExpect(jsonPath("currentBalance").value("123456789012345.67"))
            .andReturn().getResponse();
        UUID account=UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText());
        mvc.perform(get("/api/v1/accounts/"+account+"/ledger").header("Authorization","Bearer "+user.access()).header("X-Workspace-Id",workspace))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(1))
            .andExpect(jsonPath("items[0].kind").value("OPENING_BALANCE"))
            .andExpect(jsonPath("items[0].amount").value("123456789012345.67"));
        assertThatThrownBy(()->jdbc.update("UPDATE account_ledger_entries SET amount=0 WHERE account_id=?",account))
            .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(()->jdbc.update("DELETE FROM account_ledger_entries WHERE account_id=?",account))
            .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("immutable");
    }
    @Test void retriesReplayOriginalResponseAndRejectDifferentData() throws Exception {
        var user=verifiedUser();var workspace=workspace(user);var key=UUID.randomUUID();
        var first=create(user,workspace,key,draft("100.00")).andExpect(status().isCreated()).andReturn().getResponse();
        var replay=create(user,workspace,key,draft("100")).andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed","true"))
            .andReturn().getResponse();
        assertThat(json.readTree(replay.getContentAsString())).isEqualTo(json.readTree(first.getContentAsString()));
        create(user,workspace,key,draft("101.00")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_accounts WHERE workspace_id=?",Long.class,workspace)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger_entries WHERE workspace_id=?",Long.class,workspace)).isEqualTo(1);
    }
    @Test void concurrentRetriesCreateOneAccountAndOpening() throws Exception {
        var user=verifiedUser();var workspace=workspace(user);var key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Integer> request=()->{gate.await();return create(user,workspace,key,draft("20.01")).andReturn().getResponse().getStatus();};
            var first=pool.submit(request);var second=pool.submit(request);gate.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger_entries WHERE workspace_id=?",Long.class,workspace)).isEqualTo(1);
    }
    @Test void forbidsCrossWorkspaceAndReadOnlyWrites() throws Exception {
        var alice=verifiedUser();var bob=verifiedUser();var first=workspace(alice);var second=workspace(alice);
        var response=create(alice,first,UUID.randomUUID(),draft("0.00")).andReturn().getResponse();
        var id=json.readTree(response.getContentAsString()).get("id").asText();
        mvc.perform(get("/api/v1/accounts/"+id).header("Authorization","Bearer "+bob.access()).header("X-Workspace-Id",first)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/"+id).header("Authorization","Bearer "+alice.access()).header("X-Workspace-Id",second)).andExpect(status().isNotFound());
        jdbc.update("UPDATE workspace_members SET role_code='READ_ONLY' WHERE workspace_id=?",first);
        create(alice,first,UUID.randomUUID(),draft("0.00")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts").header("Authorization","Bearer "+alice.access()).header("X-Workspace-Id",first))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(1));
    }
    @Test void metadataChangesNeverAlterBalanceAndRejectStaleVersions() throws Exception {
        var user=verifiedUser();var workspace=workspace(user);
        var created=create(user,workspace,UUID.randomUUID(),draft("-25.00")).andExpect(status().isCreated()).andReturn().getResponse();
        var view=json.readTree(created.getContentAsString());String path="/api/v1/accounts/"+view.get("id").asText();
        var patch=Map.of("name","Archived account","status","ARCHIVED","version",view.get("version").asLong());
        mvc.perform(patch(path).header("Authorization","Bearer "+user.access()).header("X-Workspace-Id",workspace).contentType("application/json")
            .content(json.writeValueAsString(patch))).andExpect(status().isOk()).andExpect(jsonPath("currentBalance").value("-25.00"));
        mvc.perform(patch(path).header("Authorization","Bearer "+user.access()).header("X-Workspace-Id",workspace).contentType("application/json")
            .content(json.writeValueAsString(patch))).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/accounts").header("Authorization","Bearer "+user.access()).header("X-Workspace-Id",workspace).param("status","ACTIVE"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
        mvc.perform(patch(path).header("Authorization","Bearer "+user.access()).header("X-Workspace-Id",workspace).contentType("application/json")
            .content("{\"version\":1,\"currentBalance\":\"999\"}")).andExpect(status().isBadRequest());
    }
    @Test void rejectsFractionalMinorUnitsAndRollsBackIfAuditFails() throws Exception {
        var user=verifiedUser();var workspace=workspace(user);
        create(user,workspace,UUID.randomUUID(),draft("1.001")).andExpect(status().isUnprocessableEntity());
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
            .audit(any(UUID.class),eq(workspace),eq("ACCOUNT_OPENED"),eq("ACCOUNT"),any(UUID.class));
        assertThatThrownBy(()->create(user,workspace,UUID.randomUUID(),draft("20.00")))
            .hasRootCauseMessage("Synthetic audit failure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_accounts WHERE workspace_id=?",Long.class,workspace)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_creation_requests WHERE workspace_id=?",Long.class,workspace)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_ledger_entries WHERE workspace_id=?",Long.class,workspace)).isZero();
    }
    @Test void rejectsFutureOpeningAndCrossTenantLedgerReferences() throws Exception {
        var user=verifiedUser();var first=workspace(user);var second=workspace(user);
        var future=new HashMap<>(draft("0.00"));future.put("openingDate","9999-12-31");
        create(user,first,UUID.randomUUID(),future).andExpect(status().isUnprocessableEntity());
        var result=create(user,first,UUID.randomUUID(),draft("0.00")).andExpect(status().isCreated()).andReturn().getResponse();
        UUID account=UUID.fromString(json.readTree(result.getContentAsString()).get("id").asText());
        assertThatThrownBy(()->jdbc.update("""
            INSERT INTO account_ledger_entries(id,workspace_id,account_id,currency,fraction_digits,amount,kind,booking_date,created_by,created_at)
            SELECT ?,?,account_id,currency,fraction_digits,amount,kind,booking_date,created_by,created_at
            FROM account_ledger_entries WHERE account_id=?
            """,UUID.randomUUID(),second,account)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        mvc.perform(get("/api/v1/accounts").header("Authorization","Bearer "+user.access()).header("X-Workspace-Id",first)
            .param("page","0").param("size","1").param("type","SAVINGS"))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(0));
    }
}
