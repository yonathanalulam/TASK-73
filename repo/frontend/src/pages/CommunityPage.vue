<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { communityApi, type CommunityPost, type Comment } from '@/api/community';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';

const toast = useToast();
const auth = useAuthStore();
const loading = ref(false);
const posts = ref<CommunityPost[]>([]);
const selectedPost = ref<CommunityPost | null>(null);
const comments = ref<Comment[]>([]);
const commentsLoading = ref(false);
const newCommentBody = ref('');

async function load() {
  loading.value = true;
  try {
    posts.value = await communityApi.listPosts();
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to load posts');
  } finally {
    loading.value = false;
  }
}

async function selectPost(post: CommunityPost) {
  selectedPost.value = post;
  commentsLoading.value = true;
  try {
    comments.value = await communityApi.listComments(post.id);
  } catch (err) {
    toast.error('Failed to load comments');
  } finally {
    commentsLoading.value = false;
  }
}

async function submitComment() {
  if (!selectedPost.value || !newCommentBody.value.trim()) return;
  try {
    const c = await communityApi.createComment({
      postId: selectedPost.value.id,
      body: newCommentBody.value.trim(),
    });
    comments.value.push(c);
    newCommentBody.value = '';
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to post comment');
  }
}

async function likePost(post: CommunityPost) {
  try {
    await communityApi.likePost(post.id);
    toast.success('Liked');
  } catch (err) {
    toast.error('Like failed');
  }
}

async function muteThread(post: CommunityPost) {
  try {
    await communityApi.muteThread(post.id);
    toast.success('Thread muted');
  } catch (err) {
    toast.error('Mute failed');
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

onMounted(load);
</script>

<template>
  <section class="community">
    <header>
      <h1>Community</h1>
      <p class="muted">Posts and discussions from your organization.</p>
    </header>

    <div class="layout">
      <!-- Post list -->
      <div class="post-list">
        <div v-if="loading" class="placeholder">Loading...</div>

        <div v-for="p in posts" :key="p.id"
             :class="['post-card', { selected: selectedPost?.id === p.id }]"
             @click="selectPost(p)">
          <h3 v-if="p.title">{{ p.title }}</h3>
          <p class="post-body">{{ p.body.length > 120 ? p.body.slice(0, 120) + '...' : p.body }}</p>
          <div class="post-meta">
            <span>{{ formatDate(p.createdAt) }}</span>
            <span class="vis">{{ p.visibility }}</span>
          </div>
          <div class="post-actions">
            <button type="button" class="link" @click.stop="likePost(p)">Like</button>
            <button type="button" class="link" @click.stop="muteThread(p)">Mute</button>
          </div>
        </div>

        <div v-if="!loading && posts.length === 0" class="placeholder">No posts yet.</div>
      </div>

      <!-- Comment panel -->
      <div class="comment-panel" v-if="selectedPost">
        <div class="panel-header">
          <h2>{{ selectedPost.title || 'Post #' + selectedPost.id }}</h2>
          <p class="full-body">{{ selectedPost.body }}</p>
        </div>

        <div v-if="commentsLoading" class="placeholder">Loading comments...</div>

        <div v-else class="comment-list">
          <div v-for="c in comments" :key="c.id" class="comment">
            <div class="comment-meta">
              User #{{ c.authorUserId }} &middot; {{ formatDate(c.createdAt) }}
              <span v-if="c.parentCommentId" class="reply-tag">reply to #{{ c.parentCommentId }}</span>
            </div>
            <p>{{ c.body }}</p>
          </div>
          <div v-if="comments.length === 0" class="placeholder">No comments yet.</div>
        </div>

        <div class="comment-form" v-if="auth.hasPermission('community.write')">
          <textarea v-model="newCommentBody" placeholder="Write a comment..." rows="3" />
          <button type="button" @click="submitComment" :disabled="!newCommentBody.trim()">Post</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.community h1 { margin: 0 0 4px; }
.muted { color: var(--color-text-muted); margin: 0 0 16px; }
.layout { display: flex; gap: 16px; min-height: 400px; }
.post-list { width: 360px; flex-shrink: 0; display: flex; flex-direction: column; gap: 8px; overflow-y: auto; }
.post-card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 12px; cursor: pointer; transition: border-color 0.15s; }
.post-card:hover { border-color: var(--color-primary); }
.post-card.selected { border-color: var(--color-primary); background: var(--color-bg); }
.post-card h3 { margin: 0 0 4px; font-size: 14px; }
.post-body { margin: 0 0 4px; font-size: 13px; }
.post-meta { font-size: 11px; color: var(--color-text-muted); display: flex; gap: 8px; }
.post-actions { margin-top: 6px; display: flex; gap: 8px; }
.vis { background: var(--color-bg); padding: 1px 6px; border-radius: 4px; font-size: 10px; }
.link { background: none; border: none; color: var(--color-primary); cursor: pointer; padding: 0; font-size: 12px; }
.comment-panel { flex: 1; display: flex; flex-direction: column; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
.panel-header { padding: 16px; border-bottom: 1px solid var(--color-border); }
.panel-header h2 { margin: 0 0 8px; font-size: 16px; }
.full-body { margin: 0; font-size: 14px; white-space: pre-wrap; }
.comment-list { flex: 1; padding: 12px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
.comment { padding: 8px; border: 1px solid var(--color-border); border-radius: var(--radius); font-size: 13px; }
.comment-meta { font-size: 11px; color: var(--color-text-muted); margin-bottom: 4px; }
.reply-tag { background: #e0ecff; padding: 1px 4px; border-radius: 3px; font-size: 10px; }
.comment p { margin: 0; }
.comment-form { padding: 12px; border-top: 1px solid var(--color-border); display: flex; gap: 8px; align-items: flex-end; }
.comment-form textarea { flex: 1; resize: vertical; padding: 8px; border: 1px solid var(--color-border); border-radius: var(--radius); font: inherit; font-size: 13px; }
.placeholder { padding: 24px; text-align: center; color: var(--color-text-muted); }
</style>
