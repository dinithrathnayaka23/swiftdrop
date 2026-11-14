import { apiClient } from './client';
import { httpServiceAreasApi as api } from './serviceAreas';

// Jest's env stand-in turns the mock APIs on (src/test/env.ts): test the HTTP implementation directly.
jest.mock('./client', () => ({
  apiClient: { get: jest.fn(), post: jest.fn(), patch: jest.fn(), delete: jest.fn() },
}));

const get = jest.mocked(apiClient.get);
const post = jest.mocked(apiClient.post);
const patch = jest.mocked(apiClient.patch);
const del = jest.mocked(apiClient.delete);

describe('serviceAreasApi (HTTP)', () => {
  beforeEach(() => jest.resetAllMocks());

  it('lists with search, active, page and size', async () => {
    get.mockResolvedValue({ data: { content: [] } });

    await api.list({ search: 'kandy', active: true, page: 0, size: 50 });

    expect(get).toHaveBeenCalledWith('/admin/service-areas', {
      params: { search: 'kandy', active: true, page: 0, size: 50 },
    });
  });

  it('reads one zone', async () => {
    get.mockResolvedValue({ data: {} });

    await api.get('z 1');

    expect(get).toHaveBeenCalledWith('/admin/service-areas/z%201');
  });

  it('posts a new zone with the CreateServiceAreaRequest fields', async () => {
    post.mockResolvedValue({ data: {} });
    const zone = {
      name: 'Kandy',
      description: 'Hill country',
      active: true,
      coverageAreas: [
        { district: 'Kandy', city: 'Kandy', postalCode: '20000' },
        { district: 'Kandy', city: 'Peradeniya' },
      ],
    };

    await api.create(zone);

    expect(post).toHaveBeenCalledWith('/admin/service-areas', zone);
  });

  it('renames, activates and deactivates', async () => {
    patch.mockResolvedValue({ data: {} });
    post.mockResolvedValue({ data: {} });

    await api.update('z1', { name: 'Kandy City' });
    await api.activate('z1');
    await api.deactivate('z1');

    expect(patch).toHaveBeenCalledWith('/admin/service-areas/z1', { name: 'Kandy City' });
    expect(post).toHaveBeenNthCalledWith(1, '/admin/service-areas/z1/activate');
    expect(post).toHaveBeenNthCalledWith(2, '/admin/service-areas/z1/deactivate');
  });

  it('adds and removes coverage rows', async () => {
    post.mockResolvedValue({ data: { id: 'c1' } });
    del.mockResolvedValue({ data: undefined });

    await expect(
      api.addCoverage('z1', { district: 'Kandy', city: 'Katugastota' }),
    ).resolves.toEqual({ id: 'c1' });
    await api.removeCoverage('z1', 'c 1');

    expect(post).toHaveBeenCalledWith('/admin/service-areas/z1/coverage-areas', {
      district: 'Kandy',
      city: 'Katugastota',
    });
    expect(del).toHaveBeenCalledWith('/admin/service-areas/z1/coverage-areas/c%201');
  });
});
