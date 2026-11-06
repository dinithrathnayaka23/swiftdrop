import { hasErrors } from '../../utils/validation';
import {
  initialAddressValues,
  initialParcelValues,
  toBookingRequest,
  validateAddressStep,
  validateParcelStep,
  type AddressValues,
  type ParcelValues,
} from './bookingForm';

const limits = { maxWeightKg: 30, maxSideCm: 100 };

const address: AddressValues = {
  ...initialAddressValues(),
  pickupName: 'Ayesha Khan',
  pickupPhone: '077 123 4567',
  pickupLine1: '123 Flower Road',
  pickupCity: 'Colombo',
  pickupDistrict: 'Colombo',
  dropoffName: 'Hassan Ali',
  dropoffPhone: '076 987 6543',
  dropoffLine1: '45 Galle Road',
  dropoffLine2: '  Near City Mall ',
  dropoffCity: 'Galle',
  dropoffDistrict: 'Galle',
  dropoffPostalCode: '80000',
};

const parcel: ParcelValues = { ...initialParcelValues(), weightKg: '2.5' };

describe('validateAddressStep', () => {
  it('accepts complete addresses', () => {
    expect(hasErrors(validateAddressStep(address))).toBe(false);
  });

  it('reports the required fields of an empty form', () => {
    const errors = validateAddressStep(initialAddressValues());

    expect(errors.pickupName).toBeDefined();
    expect(errors.pickupPhone).toBeDefined();
    expect(errors.dropoffDistrict).toBeDefined();
    expect(errors.pickupLine2).toBeUndefined();
    expect(errors.pickupPostalCode).toBeUndefined();
  });

  it('refuses a district that is not in the list', () => {
    const errors = validateAddressStep({ ...address, pickupDistrict: 'Atlantis' });

    expect(errors.pickupDistrict).toBe('Select a district');
  });
});

describe('validateParcelStep', () => {
  it('accepts a parcel without sizes', () => {
    expect(hasErrors(validateParcelStep(parcel, limits))).toBe(false);
  });

  it('refuses a weight above the limit', () => {
    const errors = validateParcelStep({ ...parcel, weightKg: '31' }, limits);

    expect(errors.weightKg).toContain('at most 30');
  });

  it('refuses a weight of zero', () => {
    expect(validateParcelStep({ ...parcel, weightKg: '0' }, limits).weightKg).toBeDefined();
  });

  it('needs all three sizes or none', () => {
    const errors = validateParcelStep({ ...parcel, lengthCm: '40' }, limits);

    expect(errors.widthCm).toBeDefined();
    expect(errors.heightCm).toBeDefined();
    expect(errors.lengthCm).toBeUndefined();
  });

  it('refuses a side longer than the limit', () => {
    const errors = validateParcelStep(
      { ...parcel, lengthCm: '101', widthCm: '10', heightCm: '10' },
      limits,
    );

    expect(errors.lengthCm).toContain('at most 100');
  });
});

describe('toBookingRequest', () => {
  it('builds the request with an international phone number and trimmed text', () => {
    const request = toBookingRequest(address, parcel);

    expect(request.pickup.contactPhone).toBe('+94771234567');
    expect(request.dropoff.contactPhone).toBe('+94769876543');
    expect(request.dropoff.addressLine2).toBe('Near City Mall');
    expect(request.weightKg).toBe(2.5);
    expect(request.parcelType).toBe('PARCEL');
  });

  it('leaves out empty optional fields', () => {
    const request = toBookingRequest(address, parcel);

    expect(request.pickup.addressLine2).toBeUndefined();
    expect(request.pickup.postalCode).toBeUndefined();
    expect(request.lengthCm).toBeUndefined();
    expect(request.contentDescription).toBeUndefined();
  });
});
