const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type ApiRequestOptions = RequestInit & {
  token?: string;
};

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {}
): Promise<T> {
  const { token, headers, ...requestOptions } = options;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers
    }
  });

  if (!response.ok) {
    const errorBody = await parseBody(response);
    throw new ApiError(response.status, extractErrorMessage(errorBody, response.status), errorBody);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return parseBody(response) as Promise<T>;
}

async function parseBody(response: Response) {
  const text = await response.text();
  if (!text) return null;

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json") && !contentType.includes("application/problem+json")) {
    return text;
  }

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function extractErrorMessage(body: unknown, status: number) {
  if (body && typeof body === "object") {
    const detail = "detail" in body ? body.detail : undefined;
    const title = "title" in body ? body.title : undefined;
    if (typeof detail === "string" && detail) return detail;
    if (typeof title === "string" && title) return title;
  }
  return `API request failed with status ${status}`;
}
