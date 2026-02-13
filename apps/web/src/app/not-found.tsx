import Link from "next/link";

export default function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center p-8">
        <h2 className="text-6xl font-bold text-slate-200 mb-4">404</h2>
        <h3 className="text-xl font-bold text-slate-900 mb-2">
          Page not found
        </h3>
        <p className="text-slate-500 mb-6">
          The page you are looking for does not exist or has been moved.
        </p>
        <Link
          href="/"
          className="inline-block px-6 py-2.5 bg-sky-500 text-white rounded-lg font-medium hover:bg-sky-600 transition-colors"
        >
          Go home
        </Link>
      </div>
    </div>
  );
}
