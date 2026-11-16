import { ApiRequestError } from '../../api/errors';
import {
  courierErrorsFrom,
  initials,
  toNewCourier,
  validateNewCourier,
  validateRegistration,
  type CourierFormValues,
} from './courierRules';

const valid: CourierFormValues = {
  fullName: "Nuwan D'Silva-Perera",
  email: 'nuwan@example.com',
  countryCode: '+94',
  phone: '077 123 4567',
  password: 'courier2026',
  vehicleType: 'Motorbike',
  vehicleRegistration: 'WP CAB-1234',
  serviceAreaIds: ['z1'],
};

describe('courier rules (as the backend)', () => {
  it('accepts a valid courier and sends the phone in E.164', () => {
    expect(validateNewCourier(valid)).toEqual({});
    expect(toNewCourier(valid)).toMatchObject({ phone: '+94771234567', serviceAreaIds: ['z1'] });
  });

  it('accepts letters from any language in names', () => {
    expect(validateNewCourier({ ...valid, fullName: 'Kumārasiṅha Rājapakṣa' })).toEqual({});
  });

  it.each([
    ['fullName', { fullName: 'R2D2' }],
    ['email', { email: 'not-an-email' }],
    ['phone', { phone: '12' }],
    ['password', { password: 'short1' }],
    ['password', { password: 'onlyletters' }],
    ['password', { password: ' spaced123' }],
    ['vehicleType', { vehicleType: ' ' }],
    ['vehicleRegistration', { vehicleRegistration: 'AB@12' }],
  ])('flags %s', (field, change) => {
    expect(validateNewCourier({ ...valid, ...change })).toHaveProperty(field);
  });

  it('checks registrations like the backend', () => {
    expect(validateRegistration('wp  cab 1234')).toBeUndefined();
    expect(validateRegistration('-ABC')).toBeDefined();
    expect(validateRegistration('A'.repeat(31))).toBeDefined();
  });

  it('puts API field errors on their fields and the inactive-area 400 on the picker', () => {
    const conflict = new ApiRequestError('taken', {
      status: 409,
      fieldErrors: [
        {
          field: 'vehicleRegistration',
          message: 'A courier with this vehicle registration already exists',
        },
      ],
    });
    expect(courierErrorsFrom(conflict)).toEqual({
      fields: { vehicleRegistration: 'A courier with this vehicle registration already exists' },
    });

    const inactive = new ApiRequestError('Unknown or inactive service area', { status: 400 });
    expect(courierErrorsFrom(inactive).fields.serviceAreaIds).toBeDefined();

    const other = new ApiRequestError('Something went wrong', { status: 500 });
    expect(courierErrorsFrom(other)).toEqual({ fields: {}, message: 'Something went wrong' });
  });

  it('makes initials from the first two names', () => {
    expect(initials('Ramesh Perera')).toBe('RP');
    expect(initials('Tharindu  Jayasekara Bandara')).toBe('TJ');
  });
});
