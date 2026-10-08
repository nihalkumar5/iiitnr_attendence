import { NextResponse } from "next/server";

export async function GET(request: Request) {
  const requestUrl = new URL(request.url);
  const code = requestUrl.searchParams.get("code");
  const error = requestUrl.searchParams.get("error");
  const errorDescription = requestUrl.searchParams.get("error_description");
  const next = requestUrl.searchParams.get("next") || "/student";

  const redirectUrl = new URL(next, requestUrl.origin);

  if (error) {
    redirectUrl.searchParams.set("oauth_error", errorDescription || error);
    return NextResponse.redirect(redirectUrl);
  }

  if (code) {
    redirectUrl.searchParams.set("code", code);
  }

  return NextResponse.redirect(redirectUrl);
}
