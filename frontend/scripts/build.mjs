import { cp, mkdir, rm } from 'node:fs/promises';
const target = new URL('../dist/', import.meta.url);
await rm(target, { recursive: true, force: true });
await mkdir(target, { recursive: true });
await cp(new URL('../src/', import.meta.url), target, { recursive: true });
await mkdir(new URL('vendor/', target), { recursive: true });
for (const [source, name] of [
  ['angular/angular.min.js', 'angular.min.js'],
  ['angular/angular-csp.css', 'angular-csp.css'],
  ['@uirouter/angularjs/release/angular-ui-router.min.js', 'angular-ui-router.min.js'],
  ['bootstrap/dist/css/bootstrap.min.css', 'bootstrap.min.css'],
  ['chart.js/dist/chart.umd.js', 'chart.umd.js']
]) await cp(new URL('../node_modules/' + source, import.meta.url), new URL('vendor/' + name, target));
console.log('Built FinanceHub static assets');
