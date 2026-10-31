/** Simulated network latency for the mock API. */
export function delay(ms = 500): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
