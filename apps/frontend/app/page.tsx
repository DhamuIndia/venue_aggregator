import { Search } from "lucide-react";

export default function HomePage() {
  return (
    <main className="min-h-screen bg-background">
      <section className="mx-auto flex min-h-screen w-full max-w-6xl flex-col justify-center px-6 py-16">
        <div className="max-w-3xl">
          <p className="text-sm font-medium uppercase tracking-wide text-muted-foreground">
            Venue Aggregator
          </p>
          <h1 className="mt-4 text-4xl font-semibold tracking-normal text-foreground md:text-6xl">
            Search halls, vendors, and event services from one place.
          </h1>
          <p className="mt-6 max-w-2xl text-lg leading-8 text-muted-foreground">
            This starter shell is ready for public search, hall detail pages,
            owner onboarding, vendor subscriptions, and admin approvals.
          </p>
          <div className="mt-8 flex flex-col gap-3 rounded-md border border-border bg-white p-3 shadow-sm md:flex-row">
            <label className="sr-only" htmlFor="search">
              Search location
            </label>
            <input
              id="search"
              className="min-h-12 flex-1 rounded-md border border-border px-4 text-base outline-none focus:ring-2 focus:ring-primary"
              placeholder="Search by city, area, or hall name"
            />
            <button className="inline-flex min-h-12 items-center justify-center gap-2 rounded-md bg-primary px-5 text-sm font-medium text-white">
              <Search aria-hidden="true" size={18} />
              Search
            </button>
          </div>
        </div>
      </section>
    </main>
  );
}
