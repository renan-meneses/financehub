const { test, expect } = require('@playwright/test');
const { randomUUID, randomBytes } = require('node:crypto');

test('register, verify, sign in, open an account, inspect its ledger and archive it', async ({ page, request, baseURL }) => {
  const email = `financehub-${randomUUID()}@example.test`;
  const password = randomBytes(24).toString('base64url');
  const workspaceName = `Synthetic workspace ${randomUUID().slice(0, 8)}`;
  const mailpit = process.env.E2E_MAILPIT_URL || 'http://localhost:8025';
  const browserErrors = [];
  page.on('pageerror', error => browserErrors.push(error.message));
  await page.goto('/#!/register');
  await page.getByLabel('Nome', { exact: true }).fill('Synthetic test user');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Criar conta', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Confira seu e-mail');

  let messageId;
  await expect.poll(async () => {
    const response = await request.get(`${mailpit}/api/v1/search`, { params: { query: `to:${email}` } });
    expect(response.ok()).toBeTruthy();
    const messages = (await response.json()).messages;
    messageId = messages[0]?.ID;
    return Boolean(messageId);
  }, { timeout: 40000 }).toBe(true);
  const message = await request.get(`${mailpit}/api/v1/message/${encodeURIComponent(messageId)}`);
  expect(message.ok()).toBeTruthy();
  const link = (await message.json()).Text.match(/https?:\/\/[^\s]+/)[0];
  expect(new URL(link).origin).toBe(new URL(baseURL).origin);
  await page.goto(link);
  await page.getByRole('button', { name: 'Confirmar e-mail' }).click();
  await expect(page.getByRole('status')).toContainText('E-mail confirmado');
  await page.getByRole('link', { name: 'Voltar ao login' }).click();
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Seus workspaces' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Seus workspaces' })).toBeVisible();
  await page.getByLabel('Nome do workspace').fill(workspaceName);
  await page.getByRole('button', { name: 'Criar', exact: true }).click();
  await page.locator('.workspace-card').filter({ hasText: workspaceName }).getByRole('button').click();
  await expect(page.getByRole('heading', { name: 'Contas financeiras' })).toBeVisible();
  await page.getByLabel('Nome da conta').fill('Synthetic checking account');
  await page.getByLabel('Instituição financeira').fill('Synthetic bank');
  await page.getByLabel('Saldo de abertura', { exact: true }).fill('1234.56');
  await page.getByRole('button', { name: 'Criar conta', exact: true }).click();
  const account = page.locator('.workspace-card').filter({ hasText: 'Synthetic checking account' });
  await expect(account).toContainText('1.234,56 BRL');
  await account.getByRole('button', { name: 'Histórico' }).click();
  await expect(page.getByRole('table')).toContainText('Saldo de abertura');
  await expect(page.getByRole('table').getByRole('cell', { name: '1.234,56 BRL', exact: true })).toHaveCount(2);
  await expect.poll(() => page.evaluate(() =>
    document.documentElement.scrollWidth <= document.documentElement.clientWidth
  )).toBe(true);
  await account.getByRole('button', { name: 'Editar' }).click();
  await page.locator('#edit-status').selectOption('ARCHIVED');
  await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  await expect(account).toHaveCount(0);
  await page.locator('#account-status').selectOption('ARCHIVED');
  await expect(account).toContainText('1.234,56 BRL');
  await expect(page.locator('body')).not.toContainText('{{');
  expect(browserErrors).toEqual([]);
});
