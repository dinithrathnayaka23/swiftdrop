import { TestEnvironment } from 'jest-environment-jsdom';

/**
 * jsdom without the gaps react-router's data router needs: it builds a fetch `Request` for every
 * navigation, which jsdom doesn't have. Node's own are used, together with Node's AbortController,
 * because a Node `Request` only accepts a Node `AbortSignal`.
 */
export default class JsdomWithFetchEnvironment extends TestEnvironment {
  constructor(...args) {
    super(...args);
    Object.assign(this.global, {
      Request,
      Response,
      Headers,
      AbortController,
      AbortSignal,
    });
  }
}
