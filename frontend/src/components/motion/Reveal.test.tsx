import { act, render, screen } from '@testing-library/react';
import Reveal from './Reveal';

type ObserverCallback = (entries: Partial<IntersectionObserverEntry>[]) => void;

describe('Reveal', () => {
  const original = window.IntersectionObserver;
  let trigger: ObserverCallback = () => {};
  const disconnect = jest.fn();

  beforeEach(() => {
    window.IntersectionObserver = jest.fn((callback: ObserverCallback) => {
      trigger = callback;
      return { observe: jest.fn(), disconnect, unobserve: jest.fn(), takeRecords: () => [] };
    }) as unknown as typeof IntersectionObserver;
  });

  afterEach(() => {
    window.IntersectionObserver = original;
  });

  const wrapper = () => screen.getByText('content').parentElement!;

  it('starts hidden and becomes visible once scrolled into view, then stops watching', () => {
    render(
      <Reveal delay={120}>
        <p>content</p>
      </Reveal>,
    );
    expect(wrapper()).toHaveAttribute('data-visible', 'false');
    expect(wrapper()).toHaveStyle({ '--reveal-delay': '120ms' });

    act(() => trigger([{ isIntersecting: true }]));

    expect(wrapper()).toHaveAttribute('data-visible', 'true');
    expect(disconnect).toHaveBeenCalled();
  });

  it('stays hidden while out of view', () => {
    render(
      <Reveal>
        <p>content</p>
      </Reveal>,
    );

    act(() => trigger([{ isIntersecting: false }]));

    expect(wrapper()).toHaveAttribute('data-visible', 'false');
  });

  it('shows content immediately where IntersectionObserver is not supported', () => {
    // @ts-expect-error simulating an old browser
    delete window.IntersectionObserver;
    render(
      <Reveal>
        <p>content</p>
      </Reveal>,
    );

    expect(wrapper()).toHaveAttribute('data-visible', 'true');
  });
});
