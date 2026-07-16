export function normalizeExternalUrl(value) {
  if (typeof value !== "string" || value.trim() === "") return null;
  try {
    const url = new URL(value.trim());
    if (url.protocol !== "https:" || url.username || url.password || !url.hostname) {
      return null;
    }
    return url.href;
  } catch {
    return null;
  }
}


export function buildSiteConfig(environment) {
  return {
    githubUrl: normalizeExternalUrl(environment.VITE_GITHUB_URL),
    sponsorUrl: normalizeExternalUrl(environment.VITE_SPONSOR_URL),
  };
}
