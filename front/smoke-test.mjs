/**
 * 前端模块冒烟检查（仅用于本地验证，可随时删除）
 *
 * 做法：搭一个极简的 DOM 桩，真实 import 所有模块。
 * 这样不仅能查出语法错误，还能查出 import 路径错误和首屏渲染阶段的运行时错误。
 *
 * 用法： node smoke-test.mjs
 */

/** 记录所有被赋过 innerHTML 的节点，便于检查渲染结果 */
const renderedHtml = [];

/** 极简元素桩 */
function createElementStub(tag = 'div') {
  const el = {
    tagName: String(tag).toUpperCase(),
    _html: '',
    children: [],
    style: {},
    dataset: {},
    value: '',
    checked: false,
    disabled: false,
    hidden: false,
    type: '',
    files: [],
    classList: {
      _set: new Set(),
      add(...names) { names.forEach((n) => this._set.add(n)); },
      remove(...names) { names.forEach((n) => this._set.delete(n)); },
      toggle(name, force) {
        const on = force === undefined ? !this._set.has(name) : force;
        if (on) {
          this._set.add(name);
        } else {
          this._set.delete(name);
        }
        return on;
      },
      contains(name) { return this._set.has(name); },
    },
    get innerHTML() {
      return this._html;
    },
    set innerHTML(html) {
      this._html = String(html);
      renderedHtml.push(this._html);
    },
    addEventListener() {},
    removeEventListener() {},
    appendChild(child) { this.children.push(child); return child; },
    removeChild() {},
    querySelector() { return createElementStub(); },
    querySelectorAll() { return []; },
    closest() { return createElementStub(); },
    setAttribute() {},
    getAttribute() { return null; },
    removeAttribute() {},
    click() {},
    focus() {},
    getContext() { return { drawImage() {} }; },
    toBlob() {},
  };
  return el;
}

/** 安装 DOM / 浏览器全局桩 */
function installDom() {
  const documentStub = {
    body: createElementStub('body'),
    createElement: (tag) => createElementStub(tag),
    querySelector: () => createElementStub(),
    querySelectorAll: () => [],
    addEventListener() {},
    removeEventListener() {},
  };
  globalThis.document = documentStub;
  globalThis.window = {
    location: { hash: '#/' },
    addEventListener() {},
    removeEventListener() {},
  };
  globalThis.location = globalThis.window.location;
  globalThis.MutationObserver = class {
    observe() {}

    disconnect() {}
  };
  globalThis.Image = class {
    set src(value) { this._src = value; }

    get src() { return this._src; }
  };
  globalThis.alert = () => {};
  // 拦截所有后端请求，避免冒烟测试依赖后端
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => ({ code: 40100, data: null, message: '未登录' }),
  });
}

installDom();

const modules = [
  './js/api.js',
  './js/ui.js',
  './js/app.js',
  './js/components/picture.js',
  './js/views/auth.js',
  './js/views/gallery.js',
  './js/views/mine.js',
  './js/views/adminPicture.js',
  './js/views/adminUser.js',
];

let failed = 0;
for (const spec of modules) {
  try {
    await import(spec);
    console.log(`OK   ${spec}`);
  } catch (e) {
    failed += 1;
    console.log(`FAIL ${spec}`);
    console.log(`     ${e.stack ? e.stack.split('\n').slice(0, 3).join('\n     ') : e.message}`);
  }
}

// app.js 是异步 bootstrap，等它跑完再检查首屏
await new Promise((resolve) => setTimeout(resolve, 300));

const shell = renderedHtml.find((html) => html.includes('class="header"'));
if (shell) {
  console.log('OK   首屏骨架渲染成功（含 header / nav / main）');
  if (!shell.includes('id="view"')) {
    failed += 1;
    console.log('FAIL 骨架中缺少 #view 容器');
  }
} else {
  failed += 1;
  console.log('FAIL 未渲染出页面骨架，最后一段输出：');
  console.log(renderedHtml.slice(-1)[0]);
}

console.log(`\n共检查 ${modules.length} 个模块，失败 ${failed} 项`);
process.exit(failed === 0 ? 0 : 1);
