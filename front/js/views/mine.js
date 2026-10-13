/**
 * 我的图片页：查看、编辑、重新上传、删除自己上传的图片
 */
import { pictureApi } from '../api.js';
import {
  $, $$, state, toast, escapeHtml, formatTime, formatSize,
  renderPager, renderEmpty, confirmDialog, REVIEW_STATUS, normalizePage,
} from '../ui.js';
import { openPictureDetail, openEditModal, openUploadModal, thumbUrl } from '../components/picture.js';

/** 每页数量，后端限制不能超过 20 */
const PAGE_SIZE = 12;

const query = {
  current: 1,
  pageSize: PAGE_SIZE,
  sortField: 'createTime',
  sortOrder: 'descend',
};

/** 拉取当前登录用户的图片 */
async function load(el) {
  const grid = $('#grid', el);
  grid.innerHTML = '<div class="loading">加载中…</div>';
  try {
    const data = await pictureApi.listVOByPage({
      ...query,
      userId: state.getCurrentUser().id,
    });
    renderGrid(el, normalizePage(data));
  } catch (e) {
    grid.innerHTML = renderEmpty(e.message);
  }
}

/** 删除图片 */
async function removePicture(el, id) {
  const confirmed = await confirmDialog('删除后无法恢复，确定要删除这张图片吗？');
  if (!confirmed) {
    return;
  }
  try {
    await pictureApi.delete(id);
    toast('删除成功', 'success');
    load(el);
  } catch (e) {
    toast(e.message, 'error');
  }
}

/** 渲染卡片列表 */
function renderGrid(el, page) {
  const records = page.records || [];
  const grid = $('#grid', el);
  const pager = $('#pager', el);

  if (records.length === 0) {
    grid.innerHTML = renderEmpty('你还没有上传过图片，点右上角「上传图片」试试');
    pager.innerHTML = '';
    return;
  }

  grid.innerHTML = records.map((item) => {
    const review = REVIEW_STATUS[item.reviewStatus] || { text: '未知', cls: 'tag-pending' };
    return `
      <article class="card" data-id="${item.id}">
        <div class="card-img">
          <img src="${escapeHtml(thumbUrl(item))}" alt="${escapeHtml(item.name)}" loading="lazy">
          <span class="card-badge ${review.cls}">${review.text}</span>
        </div>
        <div class="card-body">
          <h3 class="card-title" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</h3>
          <p class="card-desc">${escapeHtml(item.introduction) || '暂无简介'}</p>
          <div class="card-meta">
            <span>${formatSize(item.picSize)}</span>
            <span>${formatTime(item.createTime)}</span>
          </div>
        </div>
        <div class="card-actions">
          <button class="btn btn-sm" data-action="detail" type="button">查看</button>
          <button class="btn btn-sm" data-action="edit" type="button">编辑</button>
          <button class="btn btn-sm" data-action="reupload" type="button">重传</button>
          <button class="btn btn-sm btn-danger" data-action="delete" type="button">删除</button>
        </div>
      </article>`;
  }).join('');

  $$('[data-action]', grid).forEach((btn) => {
    const id = btn.closest('.card').dataset.id;
    btn.addEventListener('click', async () => {
      const action = btn.dataset.action;
      if (action === 'detail') {
        openPictureDetail(id, { onChanged: () => load(el) });
        return;
      }
      if (action === 'edit') {
        // 详情接口能拿到完整的图片信息，直接复用
        try {
          const picture = await pictureApi.getVOById(id);
          openEditModal(picture, { onSuccess: () => load(el) });
        } catch (e) {
          toast(e.message, 'error');
        }
        return;
      }
      if (action === 'reupload') {
        openUploadModal({ pictureId: id, onSuccess: () => load(el) });
        return;
      }
      if (action === 'delete') {
        removePicture(el, id);
      }
    });
  });

  pager.innerHTML = renderPager({
    current: page.current,
    pageSize: page.size,
    total: page.total,
  });
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
   * 渲染我的图片页
   *
   * @param {HTMLElement} el 视图容器
   */
  render(el) {
    el.innerHTML = `
      <section class="toolbar">
        <div class="toolbar-left">
          <h2 class="page-title">我的图片</h2>
          <span class="page-sub">当前账号：${escapeHtml(
    state.getCurrentUser().userName || state.getCurrentUser().userAccount,
  )}</span>
        </div>
        <div class="toolbar-right">
          <button class="btn btn-primary" id="upload-btn" type="button">上传图片</button>
        </div>
      </section>
      <section id="grid" class="grid"></section>
      <div id="pager"></div>`;

    $('#upload-btn', el).addEventListener('click', () => {
      openUploadModal({ onSuccess: () => load(el) });
    });

    load(el);
  },
};
