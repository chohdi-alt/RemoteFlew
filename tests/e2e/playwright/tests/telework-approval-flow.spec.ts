import { test, expect, request, APIRequestContext } from '@playwright/test';

const requiredEnv = [
  'KEYCLOAK_URL',
  'KEYCLOAK_REALM',
  'KEYCLOAK_CLIENT_ID',
  'EMPLOYEE_USERNAME',
  'EMPLOYEE_PASSWORD',
  'MANAGER_USERNAME',
  'MANAGER_PASSWORD',
  'HR_USERNAME',
  'HR_PASSWORD',
];

function missingEnv(): string[] {
  return requiredEnv.filter((key) => !process.env[key]);
}

async function fetchAccessToken(
  api: APIRequestContext,
  username: string,
  password: string
): Promise<string> {
  const keycloakUrl = process.env.KEYCLOAK_URL!;
  const realm = process.env.KEYCLOAK_REALM!;
  const clientId = process.env.KEYCLOAK_CLIENT_ID!;
  const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET;

  const form = new URLSearchParams();
  form.set('grant_type', 'password');
  form.set('client_id', clientId);
  form.set('username', username);
  form.set('password', password);
  if (clientSecret) {
    form.set('client_secret', clientSecret);
  }

  const response = await api.post(
    `${keycloakUrl}/realms/${realm}/protocol/openid-connect/token`,
    {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      data: form.toString(),
    }
  );

  expect(response.ok()).toBeTruthy();
  const json = await response.json();
  expect(json.access_token).toBeTruthy();
  return json.access_token as string;
}

async function findLatestRequestId(api: APIRequestContext, employeeToken: string): Promise<number> {
  const response = await api.get('/api/employee/telework', {
    headers: { Authorization: `Bearer ${employeeToken}` },
  });
  expect(response.ok()).toBeTruthy();

  const items = (await response.json()) as Array<{ requestId: number }>;
  expect(items.length).toBeGreaterThan(0);
  return items[0].requestId;
}

async function findPendingTaskKey(
  api: APIRequestContext,
  token: string,
  requestId: number
): Promise<string> {
  const response = await api.get('/api/telework/validations/pending?page=0&size=50', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  const json = await response.json();
  const tasks = (json.content ?? json) as Array<{ requestId: number; taskKey: string }>;
  const target = tasks.find((task) => task.requestId === requestId);
  expect(target).toBeTruthy();
  return target!.taskKey;
}

test.describe('RemoteFlow E2E: telework lifecycle', () => {
  test.skip(missingEnv().length > 0, `Missing environment variables: ${missingEnv().join(', ')}`);

  test('login -> dashboard + employee submit -> manager approve -> hr approve', async ({ baseURL }) => {
    const api = await request.newContext({ baseURL });

    const employeeToken = await fetchAccessToken(
      api,
      process.env.EMPLOYEE_USERNAME!,
      process.env.EMPLOYEE_PASSWORD!
    );
    const managerToken = await fetchAccessToken(
      api,
      process.env.MANAGER_USERNAME!,
      process.env.MANAGER_PASSWORD!
    );
    const hrToken = await fetchAccessToken(
      api,
      process.env.HR_USERNAME!,
      process.env.HR_PASSWORD!
    );

    const dashboardResponse = await api.get('/api/dashboard/employee', {
      headers: { Authorization: `Bearer ${employeeToken}` },
    });
    expect(dashboardResponse.ok()).toBeTruthy();

    const createPayload = {
      startDate: '2026-04-20',
      endDate: '2026-04-21',
      reason: 'E2E automated validation',
    };

    const createResponse = await api.post('/api/telework', {
      headers: { Authorization: `Bearer ${employeeToken}` },
      multipart: {
        data: {
          name: 'data.json',
          mimeType: 'application/json',
          buffer: Buffer.from(JSON.stringify(createPayload)),
        },
        file: {
          name: 'justificatif.pdf',
          mimeType: 'application/pdf',
          buffer: Buffer.from('%PDF-1.4 E2E'),
        },
      },
    });
    expect(createResponse.ok()).toBeTruthy();

    const requestId = await findLatestRequestId(api, employeeToken);

    const managerTaskKey = await findPendingTaskKey(api, managerToken, requestId);
    const managerApproveResponse = await api.post(
      `/api/telework/${requestId}/manager/approve?taskKey=${managerTaskKey}`,
      {
        headers: {
          Authorization: `Bearer ${managerToken}`,
          'Content-Type': 'application/json',
        },
        data: { comment: 'Approved by manager in E2E' },
      }
    );
    expect(managerApproveResponse.ok()).toBeTruthy();

    const hrTaskKey = await findPendingTaskKey(api, hrToken, requestId);
    const hrApproveResponse = await api.post(
      `/api/telework/${requestId}/hr/approve?taskKey=${hrTaskKey}`,
      {
        headers: {
          Authorization: `Bearer ${hrToken}`,
          'Content-Type': 'application/json',
        },
        data: { comment: 'Approved by HR in E2E' },
      }
    );
    expect(hrApproveResponse.ok()).toBeTruthy();

    const finalStatusResponse = await api.get(`/api/telework/${requestId}`, {
      headers: { Authorization: `Bearer ${managerToken}` },
    });
    expect(finalStatusResponse.ok()).toBeTruthy();

    const finalStatus = await finalStatusResponse.json();
    expect(finalStatus.status).toBe('APPROVED');
  });
});
