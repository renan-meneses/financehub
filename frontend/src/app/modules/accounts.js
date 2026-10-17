/* global angular */
(function () {
  'use strict';
  angular.module('financehub')
    .factory('AccountApi', ['$http', '$window', function (http, window) {
      return {
        forWorkspace: function (workspace) {
          var pending = null;
          function headers() { return { 'X-Workspace-Id': workspace }; }
          return {
            list: function (filters) { return http.get('/api/v1/accounts', { headers: headers(), params: filters }); },
            create: function (draft) {
              var body = angular.copy(draft), fingerprint = JSON.stringify(body);
              if (!pending || pending.fingerprint !== fingerprint) pending = { fingerprint: fingerprint, key: window.crypto.randomUUID() };
              var operation = pending;
              var requestHeaders = headers(); requestHeaders['Idempotency-Key'] = operation.key;
              return http.post('/api/v1/accounts', body, { headers: requestHeaders }).then(function (response) {
                if (pending === operation) pending = null;
                return response;
              });
            },
            update: function (id, changes) { return http.patch('/api/v1/accounts/' + id, changes, { headers: headers() }); },
            history: function (id, page) {
              return http.get('/api/v1/accounts/' + id + '/ledger', { headers: headers(), params: { page: page, size: 20 } });
            }
          };
        }
      };
    }])
    .filter('moneyText', ['I18n', function (i18n) {
      var format = function (value, currency) {
        if (typeof value !== 'string' || !/^-?[0-9]+(?:\.[0-9]+)?$/.test(value)) return '—';
        var parts = value.split('.'), english = i18n.language === 'en';
        var integer = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, english ? ',' : '.');
        return integer + (parts[1] ? (english ? '.' : ',') + parts[1] : '') + ' ' + currency;
      };
      format.$stateful = true; return format;
    }])
    .component('accountPage', {
      bindings: { workspace: '<' }, templateUrl: 'app/modules/accounts.html',
      controller: ['AccountApi', function (accounts) {
        var vm = this, api, historyRequest = 0;
        vm.types = ['CHECKING','SAVINGS','CASH','DIGITAL','INVESTMENT','BUSINESS','OTHER'];
        vm.filters = { page: 0, size: 12, status: 'ACTIVE' }; vm.total = 0; vm.items = [];
        vm.$onInit = function () {
          api = accounts.forWorkspace(vm.workspace.id);
          vm.canWrite = ['ORGANIZATION_ADMIN','FINANCIAL_MANAGER','MEMBER'].indexOf(vm.workspace.role) !== -1;
          var parts = new Intl.DateTimeFormat('en', { timeZone: vm.workspace.timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
          var date = {}; parts.forEach(function (p) { date[p.type] = p.value; });
          vm.draft = { name: '', institution: '', type: 'CHECKING', currency: vm.workspace.currency,
            openingBalance: '0.00', openingDate: date.year + '-' + date.month + '-' + date.day };
          vm.load();
        };
        function fail(error) {
          var code = error.data && error.data.code;
          vm.error = code === 'stale_version' ? 'staleAccount' : code === 'idempotency_conflict' ? 'accountRetryConflict' :
            error.status === 403 ? 'noFinancialWrite' : error.status === 422 ? 'invalidAccount' : 'error';
        }
        vm.load = function () {
          vm.loading = true; vm.error = null;
          var filters = angular.copy(vm.filters);
          if (!filters.status) delete filters.status;
          if (!filters.type) delete filters.type;
          return api.list(filters).then(function (r) { vm.items = r.data.items; vm.total = r.data.total; })
            .catch(fail).finally(function () { vm.loading = false; });
        };
        vm.filter = function () { vm.filters.page = 0; vm.load(); };
        vm.move = function (delta) { vm.filters.page += delta; vm.load(); };
        vm.create = function () {
          if (vm.saving || !vm.canWrite) return;
          vm.saving = true; vm.error = null; vm.message = null;
          api.create(vm.draft).then(function () {
            vm.message = 'accountCreated'; vm.draft.name = ''; vm.draft.institution = ''; vm.draft.openingBalance = '0.00';
            vm.filters.page = 0; return vm.load();
          }).catch(fail).finally(function () { vm.saving = false; });
        };
        vm.edit = function (item) { vm.editing = angular.copy(item); vm.error = null; };
        vm.save = function () {
          if (vm.saving || !vm.canWrite) return;
          vm.saving = true;
          api.update(vm.editing.id, { name: vm.editing.name, institution: vm.editing.institution,
            status: vm.editing.status, version: vm.editing.version }).then(function () {
            vm.editing = null; vm.message = 'accountSaved'; return vm.load();
          }).catch(fail).finally(function () { vm.saving = false; });
        };
        vm.history = function (item, page) {
          var request = ++historyRequest;
          vm.historyLoading = true; vm.historyError = false; vm.historyAccount = item; vm.historyPage = page || 0;
          api.history(item.id, vm.historyPage).then(function (r) {
            if (request === historyRequest) { vm.entries = r.data.items; vm.historyTotal = r.data.total; }
          }).catch(function () { if (request === historyRequest) vm.historyError = true; })
            .finally(function () { if (request === historyRequest) vm.historyLoading = false; });
        };
      }]
    });
}());
