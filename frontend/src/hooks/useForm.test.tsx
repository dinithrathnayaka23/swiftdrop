import { act, renderHook } from '@testing-library/react';
import type { ChangeEvent, FormEvent } from 'react';
import { ApiRequestError } from '../api/errors';
import { useForm } from './useForm';

const validate = (v: { email: string; name: string }) => ({
  email: v.email ? undefined : 'Email is required',
  name: v.name ? undefined : 'Name is required',
});

function setup() {
  return renderHook(() => useForm({ email: '', name: '' }, validate));
}

const change = (value: string) => ({ target: { value } }) as ChangeEvent<HTMLInputElement>;

function submitEvent() {
  const form = document.createElement('form');
  for (const name of ['email', 'name']) {
    const input = document.createElement('input');
    input.name = name;
    form.appendChild(input);
  }
  document.body.appendChild(form);
  return {
    preventDefault: jest.fn(),
    currentTarget: form,
  } as unknown as FormEvent<HTMLFormElement>;
}

describe('useForm', () => {
  it('hides errors until a field is left (blur)', () => {
    const { result } = setup();
    expect(result.current.errors).toEqual({});

    act(() => result.current.field('email').onBlur());

    expect(result.current.errors).toEqual({ email: 'Email is required' });
  });

  it('updates a shown error as the user types', () => {
    const { result } = setup();
    act(() => result.current.field('email').onBlur());

    act(() => result.current.field('email').onChange(change('a@b.lk')));

    expect(result.current.values.email).toBe('a@b.lk');
    expect(result.current.errors.email).toBeUndefined();
  });

  it('on an invalid submit: shows every error, focuses the first field, skips the handler', () => {
    const { result } = setup();
    const onValid = jest.fn();
    const event = submitEvent();

    act(() => result.current.handleSubmit(onValid)(event));

    expect(event.preventDefault).toHaveBeenCalled();
    expect(onValid).not.toHaveBeenCalled();
    expect(result.current.errors).toEqual({
      email: 'Email is required',
      name: 'Name is required',
    });
    expect(document.activeElement).toHaveAttribute('name', 'email');
  });

  it('calls the handler with the values when valid', () => {
    const { result } = setup();
    const onValid = jest.fn();
    act(() => {
      result.current.setValue('email', 'a@b.lk');
      result.current.setValue('name', 'Nimal');
    });

    act(() => result.current.handleSubmit(onValid)(submitEvent()));

    expect(onValid).toHaveBeenCalledWith({ email: 'a@b.lk', name: 'Nimal' });
  });

  it('shows server field errors until that field is edited', () => {
    const { result } = setup();
    const err = new ApiRequestError('Conflict', {
      status: 409,
      fieldErrors: [
        { field: 'email', message: 'An account with this email already exists' },
        { field: 'unknown', message: 'not a field of this form' },
      ],
    });

    let applied = false;
    act(() => {
      applied = result.current.applyServerErrors(err);
    });

    expect(applied).toBe(true);
    expect(result.current.errors).toEqual({ email: 'An account with this email already exists' });

    act(() => result.current.field('email').onChange(change('other@b.lk')));
    expect(result.current.errors.email).toBeUndefined();
  });

  it('applyServerErrors returns false when nothing belongs to the form', () => {
    const { result } = setup();

    expect(result.current.applyServerErrors(new Error('network'))).toBe(false);
    expect(
      result.current.applyServerErrors(
        new ApiRequestError('x', { fieldErrors: [{ field: 'other', message: 'm' }] }),
      ),
    ).toBe(false);
  });
});
