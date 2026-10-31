// Browser APIs that real browsers have but Jest's jsdom environment doesn't (needed by react-router).
import { TextDecoder, TextEncoder } from 'node:util';

Object.assign(globalThis, { TextEncoder, TextDecoder });
