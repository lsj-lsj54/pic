/**
 * 通用 UI 工具与全局状态
 */

/** 转义 HTML，所有渲染到页面的用户输入都要过一遍，避免 XSS */
export function escapeHtml(str) {
  if (str === null || str === undefined) {
    return '';
  }
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** 简写 querySelector */
export const $ = (selector, root = document) => root.querySelector(selector);

/** 简写 querySelectorAll，返回真数组 */
export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

/* ------------------------------ 全局状态 ------------------------------ */

export const state = {
  /** 当前登录用户，未登录为 null */
  currentUser: null,

  /** 获取当前登录用户 */
  getCurrentUser() {
    return this.currentUser;
  },

  /** 设置当前登录用户 */
  setCurrentUser(user) {
    this.currentUser = user || null;
  },

  /** 是否已登录 */
  isLogin() {
    return !!(this.currentUser && this.currentUser.id);
  },

  /** 是否是管理员 */
  isAdmin() {
    return this.isLogin() && this.currentUser.userRole === 'admin';
  },
};

/* ------------------------------ 常量 ------------------------------ */

/** 审核状态：0-待审核 1-通过 2-拒绝 */
export const REVIEW_STATUS = {
  0: { text: '待审核', cls: 'tag-pending' },
  1: { text: '通过', cls: 'tag-pass' },
  2: { text: '拒绝', cls: 'tag-reject' },
};

/** 图片分类 */
export const CATEGORY_OPTIONS = ['风景', '人物', '动物', '动漫', '表情包', '背景', '其他'];

/* ------------------------------ 轻提示 ------------------------------ */

let toastTimer = null;

/**
 * 弹出轻提示
 *
 * @param {string} message 提示内容
 * @param {string} type    success | error | info
 */
export function toast(message, type = 'info') {
  const root = $('#toast-root');
  root.innerHTML = `<div class="toast toast-${type}">${escapeHtml(message)}</div>`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    root.innerHTML = '';
  }, 2600);
}

/* ------------------------------ 弹窗 ------------------------------ */

/**
 * 打开弹窗
 *
 * @param {object} options
 * @param {string} options.title   标题
 * @param {string} options.content 内容 HTML
 * @param {string} [options.width] 宽度，如 '720px'
 * @returns {HTMLElement} 弹窗元素，方便继续绑定事件
 */
export function showModal({ title, content, width = '520px' }) {
  const root = $('#modal-root');
  root.innerHTML = `
    <div class="modal-mask">
      <div class="modal" style="width:${width}">
        <div class="modal-head">
          <h3>${escapeHtml(title)}</h3>
          <button class="icon-btn" data-modal-close type="button" aria-label="关闭">✕</button>
        </div>
        <div class="modal-body">${content}</div>
      </div>
    </div>`;
  const mask = $('.modal-mask', root);

  // 点遮罩关闭，点弹窗内部不关闭
  mask.addEventListener('click', (e) => {
    if (e.target === mask) {
      closeModal();
    }
  });
  $$('[data-modal-close]', root).forEach((btn) => {
    btn.addEventListener('click', closeModal);
  });
  return $('.modal', root);
}

/** 关闭弹窗 */
export function closeModal() {
  $('#modal-root').innerHTML = '';
}

/**
 * 确认框
 *
 * @param {string} message 提示内容
 * @param {string} [title] 标题
 * @returns {Promise<boolean>} 用户是否点了确认
 */
export function confirmDialog(message, title = '操作确认') {
  return new Promise((resolve) => {
    const modal = showModal({
      title,
      content: `
        <p class="confirm-text">${escapeHtml(message)}</p>
        <div class="modal-foot">
          <button class="btn" data-cancel type="button">取消</button>
          <button class="btn btn-danger" data-ok type="button">确定</button>
        </div>`,
    });
    $('[data-cancel]', modal).addEventListener('click', () => {
      closeModal();
      resolve(false);
    });
    $('[data-ok]', modal).addEventListener('click', () => {
      closeModal();
      resolve(true);
    });
  });
}

/* ------------------------------ 表单 ------------------------------ */

/**
 * 读取表单中的值
 *
 * @param {HTMLElement} form 表单元素
 * @returns {object} 表单数据
 */
export function getFormValues(form) {
  const values = {};
  $$('[name]', form).forEach((el) => {
    values[el.name] = el.type === 'checkbox' ? el.checked : el.value.trim();
  });
  return values;
}

/* ------------------------------ 格式化 ------------------------------ */

/**
 * 格式化时间
 *
 * @param {string|number} time 时间戳或时间字符串
 * @returns {string} yyyy-MM-dd HH:mm
 */
export function formatTime(time) {
  if (!time) {
    return '-';
  }
  const date = new Date(time);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * 格式化文件体积
 *
 * @param {number} size 字节数
 * @returns {string} 可读体积
 */
export function formatSize(size) {
  if (size === null || size === undefined || size === '') {
    return '-';
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
}

/**
 * 截断长文本
 *
 * @param {string} text  原文本
 * @param {number} len   最大长度
 */
export function truncate(text, len = 30) {
  if (!text) {
    return '';
  }
  return text.length > len ? `${text.slice(0, len)}…` : text;
}

/**
 * 防抖
 *
 * @param {Function} fn    目标函数
 * @param {number}   delay 延迟毫秒
 */
export function debounce(fn, delay = 400) {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

/* ------------------------------ 片段 ------------------------------ */

/**
 * 审核状态标签
 *
 * @param {number} status 审核状态值
 */
export function reviewTag(status) {
  const item = REVIEW_STATUS[status] || { text: '未知', cls: 'tag-pending' };
  return `<span class="tag ${item.cls}">${item.text}</span>`;
}

/**
 * 归一化分页信息
 * <p>
 * 兜底逻辑：MyBatis-Plus 未注册分页插件时，后端既不会执行 count 也不会拼 limit，
 * 返回的是「全部记录 + total 恒为 0」。这里在 total 明显不合理时用记录数兜底，
 * 避免前端把翻页按钮全部禁掉、页面看起来空掉。
 *
 * @param {object} page 后端返回的分页对象
 * @returns {object} 归一化后的 { current, size, total, records }
 */
export function normalizePage(page) {
  const records = (page && page.records) || [];
  const size = Number(page && page.size) || records.length || 10;
  const current = Number(page && page.current) || 1;
  let total = Number(page && page.total);
  if (!Number.isFinite(total) || total < records.length) {
    // 至少要把已经拿到的记录数算进去
    total = Math.max(records.length, current * size);
  }
  return { current, size, total, records };
}

/** 分页组件 */
export function renderPager({ current, pageSize, total }) {
  const totalPage = Math.max(1, Math.ceil(total / pageSize));
  return `
    <div class="pager">
      <button class="btn btn-sm" data-page="${current - 1}" ${current <= 1 ? 'disabled' : ''}>上一页</button>
      <span class="pager-info">第 ${current} / ${totalPage} 页 · 共 ${total} 条</span>
      <button class="btn btn-sm" data-page="${current + 1}" ${current >= totalPage ? 'disabled' : ''}>下一页</button>
    </div>`;
}

/** 空状态 */
export function renderEmpty(text = '暂无数据') {
  return `<div class="empty">${escapeHtml(text)}</div>`;
}
