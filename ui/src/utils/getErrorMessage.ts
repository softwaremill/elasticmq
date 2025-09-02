export default function getErrorMessage(error: unknown): string {
  console.error(error);

  if (error instanceof Error) return error.message;
  if (typeof error === "string") return error;
  return "An unknown error occurred";
}
