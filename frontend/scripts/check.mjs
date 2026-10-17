import { readdir, readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
async function visit(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = directory + '/' + entry.name;
    if (entry.isDirectory()) await visit(file);
    else if (file.endsWith('.js')) {
      const checked = spawnSync(process.execPath, ['--check', file], { stdio: 'inherit' });
      if (checked.status !== 0) process.exit(1);
      const text = await readFile(file, 'utf8');
      if (/\beval\s*\(|new\s+Function\s*\(|ng-bind-html/.test(text)) throw new Error('Unsafe dynamic content in ' + file);
    }
  }
}
await visit('src');
console.log('JavaScript syntax and dynamic-execution checks passed');
