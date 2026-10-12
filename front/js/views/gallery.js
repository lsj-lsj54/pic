/**
 * 图片浏览页（首页）：分页、搜索、分类筛选、详情
 */
import { pictureApi } from '../api.js';
import {
  $, $$, state, toast, escapeHtml, formatTime, renderPager, renderEmpty,
  debounce, CATEGORY_OPTIONS, REVIEW_STATUS, normalizePage,
} from '../ui.js';
import { openPictureDetail, openUploadModal } from '../components/picture.js';

/** 每页数量，后端限制不能超过 20 */
const PAGE_SIZE = 12;

const query = {
  current: 1,
  pageSize: PAGE_SIZE,
  searchText: '',
  category: '',
  sortField: 'createTime',
  sortOrder: 'descend',
};

/** 拉取当前页数据并渲染 */
async function load(el) {
  const grid = $('#grid', el);
  grid.innerHTML = '<div class="loading">加载中…</div>';
  try {
    const data = await pictureApi.listVOByPage({ ...query });
    renderGrid(el, normalizePage(data));
  } catch (e) {
    grid.innerHTML = renderEmpty(e.message);
  }
}

/** 渲染图片卡片网格 */
function renderGrid(el, page) {
  const records = page.records || [];
  const grid = $('#grid', el);
  const pager = $('#pager', el);

  if (records.length === 0) {
    grid.innerHTML = renderEmpty('没有找到图片，换个关键词试试');
  } else {
    grid.innerHTML = records.map((item) => {
      const owner = item.user ? item.user.userName || item.user.userAccount : '未知用户';
      const tags = (item.tags || []).slice(0, 3)
        .map((tag) => `<span class="tag tag-sm">${escapeHtml(tag)}</span>`).join('');
      const review = REVIEW_STATUS[item.reviewStatus];
      return `
        <article class="card" data-id="${item.id}">
          <div class="card-img">
            <img src="${escapeHtml(item.url)}" alt="${escapeHtml(item.name)}" loading="lazy">
          </div>
          <div class="card-body">
            <h3 class="card-title" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</h3>
            <p class="card-desc" title="${escapeHtml(item.introduction)}">
              ${escapeHtml(item.introduction) || '暂无简介'}
            </p>
            <div class="card-tags">${tags}${review ? `<span class="tag tag-sm ${review.cls}">${review.text}</span>` : ''}</div>
            <div class="card-meta">
              <span>${escapeHtml(owner)}</span>
              <span>${formatTime(item.createTime)}</span>
            </div>
          </div>
          <div class="card-actions">
            <button class="btn btn-sm" data-action="detail" type="button">查看</button>
          </div>
        </article>`;
    }).join('');
  }

  pager.innerHTML = renderPager({
    current: page.current,
    pageSize: page.size,
    total: page.total,
  });

  // 卡片点击进详情
  $$('[data-action="detail"]', grid).forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = btn.closest('.card').dataset.id;
      openPictureDetail(id, { onChanged: () => load(el) });
    });
  });
  $$('.card-img', grid).forEach((imgBox) => {
    imgBox.addEventListener('click', () => {
      const id = imgBox.closest('.card').dataset.id;
      openPictureDetail(id, { onChanged: () => load(el) });
    });
  });

  // 分页
  $$('[data-page]', pager).forEach((btn) => {
    btn.addEventListener('click', () => {
      const target = Number(btn.dataset.page);
      if (target < 1) {
        return;
      }
      query.current = target;
      load(el);
    });
  });
}

export default {
  /**
   * 渲染浏览页
   *
   * @param {HTMLElement} el 视图容器
   */
  render(el) {
    const categoryOptions = CATEGORY_OPTIONS
      .map((item) => `<option value="${item}" ${item === query.category ? 'selected' : ''}>${item}</option>`)
      .join('');

    el.innerHTML = `
      <section class="toolbar">
        <div class="toolbar-left">
          <input id="search-input" class="input" type="search" placeholder="搜索图片名称或简介"
                 value="${escapeHtml(query.searchText)}">
          <select id="category-select" class="input input-select">
            <option value="">全部分类</option>
            ${categoryOptions}
          </select>
        </div>
        <div class="toolbar-right">
          <select id="sort-select" class="input input-select">
            <option value="createTime-descend">最新上传</option>
            <option value="createTime-ascend">最早上传</option>
            <option value="picSize-descend">体积从大到小</option>
          </select>
          <button class="btn btn-primary" id="upload-btn" type="button">上传图片</button>
        </div>
      </section>
      <section id="grid" class="grid"></section>
      <div id="pager"></div>`;

    // 搜索防抖
    $('#search-input', el).addEventListener('input', debounce((e) => {
      query.searchText = e.target.value.trim();
      query.current = 1;
      load(el);
    }, 400));

    $('#category-select', el).addEventListener('change', (e) => {
      query.category = e.target.value;
      query.current = 1;
      load(el);
    });

    $('#sort-select', el).addEventListener('change', (e) => {
      const [sortField, sortOrder] = e.target.value.split('-');
      query.sortField = sortField;
      query.sortOrder = sortOrder;
      query.current = 1;
      load(el);
    });

    $('#upload-btn', el).addEventListener('click', () => {
      if (!state.isLogin()) {
        toast('请先登录后再上传图片', 'error');
        window.location.hash = '/login';
        return;
      }
      openUploadModal({ onSuccess: () => load(el) });
    });

    load(el);
  },
};
