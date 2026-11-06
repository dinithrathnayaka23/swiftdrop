import { useState } from 'react';
import { pricingApi } from '../../../api/pricing';
import { getErrorMessage } from '../../../api/errors';
import Alert from '../../../components/ui/Alert';
import Button from '../../../components/ui/Button';
import TextField from '../../../components/ui/TextField';
import { useForm } from '../../../hooks/useForm';
import type { PricingRule } from '../../../types/pricing';
import {
  emptyPricingForm,
  toPricingForm,
  toPricingInput,
  validatePricingForm,
  type PricingFormValues,
} from '../pricingForm';

interface PricingRuleFormProps {
  /** The rule being edited, or null to add a new one. */
  rule: PricingRule | null;
  onSaved: (rule: PricingRule) => void;
  onCancel: () => void;
}

export default function PricingRuleForm({ rule, onSaved, onCancel }: PricingRuleFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const form = useForm<PricingFormValues>(
    rule ? toPricingForm(rule) : emptyPricingForm(),
    validatePricingForm,
  );

  const submit = form.handleSubmit(async (values) => {
    setFormError(null);
    setSaving(true);
    try {
      const input = toPricingInput(values, rule?.active ?? true);
      const saved = rule
        ? await pricingApi.updateRule(rule.id, input)
        : await pricingApi.createRule(input);
      onSaved(saved);
    } catch (err) {
      if (!form.applyServerErrors(err)) setFormError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  });

  return (
    <form noValidate onSubmit={submit} className="space-y-5">
      {formError && <Alert tone="error">{formError}</Alert>}

      <TextField label="Rule name" {...form.field('ruleName')} />

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Base fee (LKR)" inputMode="decimal" {...form.field('baseFee')} />
        <TextField
          label="Included weight (kg)"
          inputMode="decimal"
          hint="Weight covered by the base fee"
          {...form.field('includedWeightKg')}
        />
        <TextField
          label="Extra fee per kg (LKR)"
          inputMode="decimal"
          {...form.field('additionalFeePerKg')}
        />
        <TextField
          label="Oversize fee (LKR)"
          inputMode="decimal"
          hint="Charged when the parcel is bigger than the max volume"
          {...form.field('oversizeFee')}
        />
        <TextField
          label="Max volume (cm³)"
          inputMode="decimal"
          optional
          hint="Leave empty for no size limit"
          {...form.field('maxVolumeCm3')}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Starts on" type="date" {...form.field('effectiveFrom')} />
        <TextField
          label="Ends on"
          type="date"
          optional
          hint="Leave empty for no end date"
          {...form.field('effectiveTo')}
        />
      </div>

      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" loading={saving}>
          {rule ? 'Save changes' : 'Add rule'}
        </Button>
      </div>
    </form>
  );
}
