import Link from "next/link";

export default async function PaymentSuccessPage({ params }: { params: Promise<{ reservationId: string }> }) {
  const { reservationId } = await params;
  return (
    <div className="flex min-h-[50vh] items-center justify-center">
      <div className="max-w-sm text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-sky-50 text-3xl">
          &#10003;
        </div>
        <h1 className="mt-4 text-xl font-bold text-slate-900">Payment Successful</h1>
        <p className="mt-2 text-sm text-slate-500">
          Reservation <span className="font-mono">{reservationId}</span> has been confirmed.
        </p>
        <div className="mt-6 flex flex-col gap-2">
          <Link
            href="/my-reservations"
            className="rounded-lg bg-sky-500 px-5 py-2.5 text-sm font-medium text-white hover:bg-sky-600 transition-colors"
          >
            View My Tickets
          </Link>
          <Link
            href="/"
            className="rounded-lg border border-slate-200 px-5 py-2.5 text-sm font-medium text-slate-600 hover:border-sky-300 transition-colors"
          >
            Back to Home
          </Link>
        </div>
      </div>
    </div>
  );
}
