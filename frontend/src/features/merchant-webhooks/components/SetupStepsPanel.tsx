import { ExternalLink } from 'lucide-react';
import SectionCard from '../../shared-parcels/components/SectionCard';

const CURL_EXAMPLE = `curl -X POST http://localhost:8080/api/v1/orders ^
  -H "Content-Type: application/json" ^
  -H "X-API-Key: your-api-key" ^
  -d "{\\"externalOrderReference\\":\\"STORE-10042\\",\\"pickupAddress\\":{...},\\"dropoffAddress\\":{...},\\"parcelType\\":\\"PARCEL\\",\\"weightKg\\":2}"`;

export default function SetupStepsPanel() {
  return (
    <SectionCard
      title="Set up your integration"
      subtitle="Follow these steps to connect your store to SwiftDrop."
    >
      <ol className="space-y-5">
        <li className="flex gap-3">
          <StepNumber number={1} />
          <div>
            <p className="font-medium text-slate-900">Create an API key</p>
            <p className="mt-1 text-sm text-slate-500">
              Generate a key for your approved merchant account and keep it on your server.
            </p>
          </div>
        </li>
        <li className="flex gap-3">
          <StepNumber number={2} />
          <div>
            <p className="font-medium text-slate-900">Configure a webhook</p>
            <p className="mt-1 text-sm text-slate-500">
              Add your HTTPS endpoint on the Delivery Updates page to receive signed status events.
            </p>
          </div>
        </li>
        <li className="flex gap-3">
          <StepNumber number={3} />
          <div>
            <p className="font-medium text-slate-900">Create an order</p>
            <p className="mt-1 text-sm text-slate-500">
              Send your first order using the merchant API.
            </p>
            <pre className="mt-3 overflow-x-auto rounded-lg bg-slate-900 p-4 text-xs leading-5 text-slate-100">
              <code>{CURL_EXAMPLE}</code>
            </pre>
          </div>
        </li>
      </ol>

      <div className="mt-6 border-t border-slate-200 pt-5">
        <a
          href="/swagger-ui.html"
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-2 text-sm font-semibold text-brand-700 hover:text-brand-800"
        >
          Open Swagger API documentation
          <ExternalLink className="size-4" aria-hidden="true" />
        </a>
        <p className="mt-3 rounded-lg border border-sky-200 bg-sky-50 p-3 text-sm text-sky-800">
          Use the sandbox or a development endpoint while testing. Never expose your API key in
          browser code or commit it to source control.
        </p>
      </div>
    </SectionCard>
  );
}

function StepNumber({ number }: { number: number }) {
  return (
    <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700">
      {number}
    </span>
  );
}
