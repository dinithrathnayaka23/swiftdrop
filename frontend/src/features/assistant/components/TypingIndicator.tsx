export default function TypingIndicator() {
  return (
    <div
      role="status"
      aria-label="The assistant is typing"
      className="flex w-fit items-center gap-1 rounded-2xl rounded-bl-sm bg-slate-100 px-4 py-3"
    >
      {[0, 150, 300].map((delayMs) => (
        <span
          key={delayMs}
          className="size-2 animate-bounce rounded-full bg-slate-400 motion-reduce:animate-none"
          style={{ animationDelay: `${delayMs}ms` }}
        />
      ))}
    </div>
  );
}
