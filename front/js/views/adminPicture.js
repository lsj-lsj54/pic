/**
 * 图片管理页（仅管理员）：审核、编辑、重新上传、删除
 */
import { pictureApi } from '../api.js';
import {
  $, $$, state, toast, escapeHtml, formatTime, formatSize, truncate, debounce,
  renderPager, renderEmpty, confirmDialog, reviewTag, showModal, closeModal, normalizePage,
} from '../ui.js';
import { openEditModal, openUploadModal } from '../components/picture.js';

const PAGE_SIZE = 10;

const query = {
  current: 1,
  pageSize: PAGE_SIZE,
  // 空字符串表示全部状态
  reviewStatus: '',
  searchText: '',
  sortField: 'createTime',
  sortOrder: 'descend',
};

/** 拉取图片分页数据（实体接口，能看到审核信息） */
async function load(el) {
  const tbody = $('#tbody', el);
  tbody.innerHTML = '<tr><td colspan="7" class="loading">加载中…</td></tr>';
  try {
    const data = await pictureApi.listByPage({ ...query });
    renderTable(el, normalizePage(data));
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="7" class="loading">${escapeHtml(e.message)}</td></tr>`;
  }
}

/** 删除图片 */
async function removePicture(el, id) {
  const confirmed = await confirmDialog('删除后无法恢复，确定删除这张图片吗？');
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

/** 打开审核弹窗 */
function openReviewModal(el, picture) {
  const modal = showModal({
    title: `审核图片 · ${picture.name}`,
    content: `
      <div class="review-preview">
        <img src="${escapeHtml(picture.url)}" alt="${escapeHtml(picture.name)}">
      </div>
      <form id="review-form" class="form">
        <div class="field">
          <span>审核结果</span>
          <div class="radio-row">
            <label><input type="radio" name="reviewStatus" value="1" checked> 通过</label>
            <label><input type="radio" name="reviewStatus" value="2"> 拒绝</label>
          </div>
        </div>
        <label class="field">
          <span>审核信息</span>
          <textarea name="reviewMessage" rows="3" placeholder="可选，例如：图片内容不符合规范"></textarea>
        </label>
        <p class="form-tip">
          分类：${escapeHtml(picture.category) || '未分类'}
          · 上传者 id：${escapeHtml(picture.userId)}
          · 状态：${reviewTag(picture.reviewStatus)}
        </p>
        <div class="modal-foot">
          <button class="btn" type="button" data-modal-close>取消</button>
          <button class="btn btn-primary" type="submit">提交审核</button>
        </div>
      </form>`,
  });

  $('#review-form', modal).addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    const reviewStatus = Number($('input[name="reviewStatus"]:checked', form).value);
    const reviewMessage = $('textarea[name="reviewMessage"]', form).value.trim();
    try {
      await pictureApi.review({ id: picture.id, reviewStatus, reviewMessage });
      closeModal();
      toast('审核完成', 'success');
      load(el);
    } catch (err) {
      toast(err.message, 'error');
    }
  });
}

/** 渲染表格 */
function renderTable(el, page) {
  const records = page.records || [];
  const tbody = $('#tbody', el);
  const pager = $('#pager', el);

  if (records.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7">${renderEmpty('没有符合条件的图片')}</td></tr>`;
    pager.innerHTML = '';
    return;
  }

  tbody.innerHTML = records.map((item) => `
    <tr data-id="${item.id}">
      <td>${item.id}</td>
      <td>
        <div class="cell-pic">
          <img src="${escapeHtml(item.url)}" alt="${escapeHtml(item.name)}" loading="lazy">
          <span title="${escapeHtml(item.name)}">${escapeHtml(truncate(item.name, 16))}</span>
        </div>
      </td>
      <td>${escapeHtml(item.category) || '-'}</td>
      <td>${escapeHtml(item.userId)}</td>
      <td>${reviewTag(item.reviewStatus)}</td>
      <td>
        <div class="cell-sub">${formatSize(item.picSize)}</div>
        <div class="cell-sub">${formatTime(item.createTime)}</div>
      </td>
      <td class="cell-actions">
        ${item.reviewStatus === 0
    ? `<button class="btn btn-sm btn-primary" data-action="review" type="button">审核</button>`
    : `<button class="btn btn-sm" data-action="review" type="button">改判</button>`}
        <button class="btn btn-sm" data-action="edit" type="button">编辑</button>
        <button class="btn btn-sm" data-action="reupload" type="button">重传</button>
        <button class="btn btn-sm btn-danger" data-action="delete" type="button">删除</button>
      </td>
    </tr>`).join('');

  $$('[data-action]', tbody).forEach((btn) => {
    const row = btn.closest('tr');
    const id = row.dataset.id;
    btn.addEventListener('click', () => {
      const action = btn.dataset.action;
      if (action === 'edit' || action === 'review') {
        const picture = records.find((item) => String(item.id) === String(id));
        if (action === 'edit') {
          openEditModal({
            ...picture,
            // 实体里的 tags 是 JSON 字符串，编辑弹窗需要数组
            tags: parseTags(picture.tags),
          }, { onSuccess: () => load(el) });
        } else {
          openReviewModal(el, picture);
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

/** 实体里的 tags 是 JSON 字符串，转成数组给编辑弹窗用 */
function parseTags(tags) {
  if (!tags) {
    return [];
  }
  try {
    const parsed = JSON.parse(tags);
    return Array.isArray(parsed) ? parsed : [];
  } catch (e) {
    return [];
  }
}

export default {
  /**
   * 渲染图片管理页
   *
   * @param {HTMLElement} el 视图容器
   */
  render(el) {
    el.innerHTML = `
      <section class="toolbar">
        <div class="toolbar-left">
          <h2 class="page-title">图片管理</h2>
          <span class="page-sub">共 ${state.getCurrentUser().userName || '管理员'} 可审核全部图片</span>
        </div>
        <div class="toolbar-right">
          <button class="btn btn-primary" id="upload-btn" type="button">上传图片</button>
        </div>
      </section>
      <section class="filters">
        <input id="search-input" class="input" type="search" placeholder="搜索图片名称或简介">
        <select id="status-select" class="input input-select">
          <option value="">全部状态</option>
          <option value="0">待审核</option>
          <option value="1">已通过</option>
          <option value="2">已拒绝</option>
        </select>
      </section>
      <section class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>图片</th>
              <th>分类</th>
              <th>上传者</th>
              <th>审核状态</th>
              <th>信息</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody id="tbody"></tbody>
        </table>
      </section>
      <div id="pager"></div>`;

    $('#search-input', el).addEventListener('input', debounce((e) => {
      query.searchText = e.target.value.trim();
      query.current = 1;
      load(el);
    }, 400));

    $('#status-select', el).addEventListener('change', (e) => {
      query.reviewStatus = e.target.value;
      query.current = 1;
      load(el);
    });

    $('#upload-btn', el).addEventListener('click', () => {
      openUploadModal({ onSuccess: () => load(el) });
    });

    load(el);
  },
};
