/**
 * 我的空间页：空间列表、创建、重命名、删除、进入空间
 * <p>
 * 空间是图片的私有容器：spaceId 为空的是公共图库，任何人可见；
 * 这里的每个空间都只对创建者（和管理员）可见，空间内的图片同理。
 */
import { spaceApi } from '../api.js';
import {
  $, $$, state, toast, escapeHtml, formatTime, renderPager, renderEmpty,
  confirmDialog, showModal, closeModal, getFormValues, spaceLevelInfo,
  spaceLevelOptions, renderSpaceUsage, normalizePage,
  renderSkeleton, revealOnScroll, attachRippleAll,
} from '../ui.js';
import { navigate } from '../app.js';

/** 每页数量 */
const PAGE_SIZE = 12;

const query = {
  current: 1,
  pageSize: PAGE_SIZE,
  sortField: 'createTime',
  sortOrder: 'descend',
};

/** 拉取当前用户自己的空间 */
async function load(el) {
  const grid = $('#grid', el);
  grid.innerHTML = renderSkeleton(PAGE_SIZE);
  try {
    const data = await spaceApi.myListVOByPage({ ...query });
    renderGrid(el, normalizePage(data));
  } catch (e) {
    grid.innerHTML = renderEmpty(e.message);
  }
}

/** 渲染空间卡片 */
function renderGrid(el, page) {
  const records = page.records || [];
  const grid = $('#grid', el);
  const pager = $('#pager', el);

  if (records.length === 0) {
    grid.innerHTML = renderEmpty('还没有空间，点右上角「创建空间」开始整理你的图片');
    pager.innerHTML = '';
    return;
  }

  grid.innerHTML = records.map((item) => {
    const level = spaceLevelInfo(item.spaceLevel);
    return `
      <article class="space-card reveal" data-id="${item.id}">
        <div class="space-card-head">
          <h3 class="space-name" title="${escapeHtml(item.spaceName)}">${escapeHtml(item.spaceName)}</h3>
          <span class="tag ${level.cls}">${level.text}</span>
        </div>
        ${renderSpaceUsage(item)}
        <div class="space-card-meta">
          <span>创建于 ${formatTime(item.createTime)}</span>
        </div>
        <div class="card-actions">
          <button class="btn btn-sm btn-primary" data-action="open" type="button">进入空间</button>
          <button class="btn btn-sm" data-action="edit" type="button">重命名</button>
          <button class="btn btn-sm btn-danger" data-action="delete" type="button">删除</button>
        </div>
      </article>`;
  }).join('');

  $$('[data-action]', grid).forEach((btn) => {
    const card = btn.closest('.space-card');
    const id = card.dataset.id;
    const space = records.find((item) => String(item.id) === String(id));
    btn.addEventListener('click', () => {
      const action = btn.dataset.action;
      if (action === 'open') {
        navigate('/space/detail', { id });
        return;
      }
      if (action === 'edit') {
        openEditModal(el, space);
        return;
      }
      if (action === 'delete') {
        removeSpace(el, space);
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

  revealOnScroll(grid);
  attachRippleAll(grid);
}

/** 创建空间弹窗 */
function openCreateModal(el) {
  // 普通用户只能创建普通版空间，后端也会再校验一次
  const onlyCommon = !state.isAdmin();
  const modal = showModal({
    title: '创建空间',
    content: `
      <form id="space-form" class="form">
        <label class="field">
          <span>空间名称</span>
          <input name="spaceName" type="text" maxlength="30" required
                 placeholder="例如：我的旅行照片" autocomplete="off">
        </label>
        <label class="field">
          <span>空间级别</span>
          <select name="spaceLevel">${spaceLevelOptions(0, onlyCommon)}</select>
        </label>
        <p class="form-tip" id="space-tip">
          ${onlyCommon
    ? '普通用户只能创建普通版空间（100 张 / 100MB），升级请联系管理员。'
    : '级别决定图片数量与容量上限，创建后限额自动填好。'}
        </p>
        <div class="modal-foot">
          <button class="btn" type="button" data-modal-close>取消</button>
          <button class="btn btn-primary" type="submit">创建</button>
        </div>
      </form>`,
  });

  $('#space-form', modal).addEventListener('submit', async (e) => {
    e.preventDefault();
    const values = getFormValues(e.target);
    try {
      await spaceApi.add({
        spaceName: values.spaceName,
        spaceLevel: Number(values.spaceLevel) || 0,
      });
      closeModal();
      toast('空间创建成功', 'success');
      query.current = 1;
      load(el);
    } catch (err) {
      toast(err.message, 'error');
    }
  });
}

/** 重命名弹窗 */
function openEditModal(el, space) {
  const modal = showModal({
    title: '重命名空间',
    content: `
      <form id="space-edit-form" class="form">
        <label class="field">
          <span>空间名称</span>
          <input name="spaceName" type="text" maxlength="30" required
                 value="${escapeHtml(space.spaceName)}" autocomplete="off">
        </label>
        <div class="modal-foot">
          <button class="btn" type="button" data-modal-close>取消</button>
          <button class="btn btn-primary" type="submit">保存</button>
        </div>
      </form>`,
  });

  $('#space-edit-form', modal).addEventListener('submit', async (e) => {
    e.preventDefault();
    const values = getFormValues(e.target);
    try {
      await spaceApi.edit({ id: space.id, spaceName: values.spaceName });
      closeModal();
      toast('保存成功', 'success');
      load(el);
    } catch (err) {
      toast(err.message, 'error');
    }
  });
}

/** 删除空间 */
async function removeSpace(el, space) {
  const confirmed = await confirmDialog(
    `删除空间「${space.spaceName}」会同时删除空间内的 ${Number(space.totalCount) || 0} 张图片，且无法恢复，确定继续吗？`,
    '删除空间',
  );
  if (!confirmed) {
    return;
  }
  try {
    await spaceApi.delete(space.id);
    toast('删除成功', 'success');
    load(el);
  } catch (e) {
    toast(e.message, 'error');
  }
}

export default {
  /**
   * 渲染我的空间页
   *
   * @param {HTMLElement} el 视图容器
   */
  render(el) {
    el.innerHTML = `
      <section class="toolbar">
        <div class="toolbar-left">
          <h2 class="page-title">我的空间</h2>
          <span class="page-sub">把图片按主题分空间管理，每个空间只有你能看到</span>
        </div>
        <div class="toolbar-right">
          <button class="btn btn-primary" id="create-space-btn" type="button">创建空间</button>
        </div>
      </section>
      <section id="grid" class="space-grid"></section>
      <div id="pager"></div>`;

    $('#create-space-btn', el).addEventListener('click', () => openCreateModal(el));

    load(el);
  },
};
