// Viewit 보안 규칙 테스트 (6.4). Firebase 에뮬레이터 안에서만 돈다. 실행: firestore-tests 폴더에서 npm test
// 사람: alice(평가 작성자), bob(다른 로그인 사용자), 로그인 안 한 사람
const { test, before, after, beforeEach } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-viewit',
    firestore: { rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8') },
    storage: { rules: fs.readFileSync(path.join(__dirname, '..', 'storage.rules'), 'utf8') },
  });
});

after(async () => {
  await env.cleanup();
});

// 매 테스트 전에 같은 데이터로 시작한다 (규칙을 끄고 넣음)
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc('users/alice').set({ username: 'alice', displayName: '앨리스' });
    await db.doc('users/bob').set({ username: 'bob', displayName: '밥' });
    await db.doc('users/alice/watchLater/v1').set({ createdAt: 1 });
    await db.doc('usernames/alice').set({ uid: 'alice' });
    await db.doc('ratings/alice_v1').set({
      userId: 'alice', videoId: 'v1', categoryId: '10', score: 4, comment: '좋아요',
      visibility: 'public', likeCount: 0, commentCount: 0,
    });
    await db.doc('ratings/alice_v2').set({
      userId: 'alice', videoId: 'v2', categoryId: '10', score: 2, comment: '',
      visibility: 'private', likeCount: 0, commentCount: 0,
    });
    await db.doc('ratings/alice_v1/comments/c1').set({ userId: 'alice', content: '첫 댓글', likeCount: 0 });
    await db.doc('ratings/alice_v2/comments/c2').set({ userId: 'alice', content: '비공개 평가 댓글', likeCount: 0 });
    await db.doc('videos/v1').set({ title: '영상', ratingSum: 4 });
    await db.doc('follows/bob_alice').set({ followerId: 'bob', followingId: 'alice' });
    await db.doc('categories/10').set({ name: '음악' });
  });
});

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const guest = () => env.unauthenticatedContext().firestore();

// ---------- users ----------

test('users: 로그인한 사람은 다른 사람 프로필을 읽는다', async () => {
  await assertSucceeds(bob().doc('users/alice').get());
});

test('users: 로그인 안 하면 프로필을 못 읽는다', async () => {
  await assertFails(guest().doc('users/alice').get());
});

test('users: 본인 프로필은 고칠 수 있다 (bannerVideoId 포함)', async () => {
  await assertSucceeds(alice().doc('users/alice').update({ bannerVideoId: 'v1', displayName: '새 이름' }));
});

test('users: 남의 프로필은 못 고친다', async () => {
  await assertFails(bob().doc('users/alice').update({ bannerVideoId: 'v9' }));
});

test('users: 하위 컬렉션 쓰기는 본인만, 연동·확인 상태는 읽기도 본인만', async () => {
  await assertSucceeds(alice().doc('users/alice/watchLater/v2').set({ createdAt: 2 }));
  await assertFails(bob().doc('users/alice/watchLater/v2').set({ createdAt: 2 }));
  await assertFails(bob().doc('users/alice/ratingReadStates/bob').set({ lastCheckedAt: 1 }));
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/alice/integrations/youtube').set({ connected: true });
  });
  await assertFails(bob().doc('users/alice/integrations/youtube').get());
});

// ---------- 공개 범위 (6.3) ----------

const setPrivacy = (data) => env.withSecurityRulesDisabled(async (ctx) => {
  await ctx.firestore().doc('users/alice/settings/privacy').set(data);
});

test('privacy: 본인만 정해진 값으로 저장하고, 누구나 읽는다', async () => {
  await assertSucceeds(alice().doc('users/alice/settings/privacy').set({ watchLaterVisibility: 'followers', updatedAt: 1 }));
  await assertFails(alice().doc('users/alice/settings/privacy').set({ watchLaterVisibility: 'friends' }));
  await assertFails(alice().doc('users/alice/settings/privacy').set({ email: 'a@b.c' }));
  await assertFails(bob().doc('users/alice/settings/privacy').set({ watchLaterVisibility: 'public' }));
  await assertSucceeds(bob().doc('users/alice/settings/privacy').get());
});

test('privacy: 설정이 없으면 전체 공개라 다른 사람도 보관함을 읽는다', async () => {
  await assertSucceeds(bob().doc('users/alice/watchLater/v1').get());
  await assertSucceeds(bob().collection('users/alice/watchLater').orderBy('createdAt', 'desc').limit(50).get());
});

test('privacy: 나만 보기면 본인만 읽는다', async () => {
  await setPrivacy({ watchLaterVisibility: 'private' });
  await assertFails(bob().doc('users/alice/watchLater/v1').get());
  await assertSucceeds(alice().doc('users/alice/watchLater/v1').get());
});

test('privacy: 팔로워만이면 팔로우한 사람만 읽는다', async () => {
  await setPrivacy({ watchLaterVisibility: 'followers' });
  await assertSucceeds(bob().doc('users/alice/watchLater/v1').get());   // bob은 alice를 팔로우함
  await assertFails(env.authenticatedContext('carol').firestore().doc('users/alice/watchLater/v1').get());
});

test('privacy: 최근 본 영상은 따로 정하고, 나를 차단한 사람 것은 못 본다', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/alice/watchHistory/v1').set({ lastWatchedAt: 1 });
  });
  await setPrivacy({ watchLaterVisibility: 'public', watchHistoryVisibility: 'private' });
  await assertSucceeds(bob().doc('users/alice/watchLater/v1').get());
  await assertFails(bob().doc('users/alice/watchHistory/v1').get());
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/alice/blockedUsers/bob').set({ createdAt: 1 });
  });
  await assertFails(bob().doc('users/alice/watchLater/v1').get());
});

// ---------- usernames ----------

test('usernames: 본인 uid로 아이디를 예약한다', async () => {
  await assertSucceeds(bob().doc('usernames/bob_new').set({ uid: 'bob' }));
});

test('usernames: 남의 uid로 예약하거나 형식이 틀리면 막는다', async () => {
  await assertFails(bob().doc('usernames/carol').set({ uid: 'alice' }));
  await assertFails(bob().doc('usernames/AB').set({ uid: 'bob' }));
});

test('usernames: 본인 아이디만 지운다', async () => {
  await assertFails(bob().doc('usernames/alice').delete());
  await assertSucceeds(alice().doc('usernames/alice').delete());
});

// ---------- follows ----------

test('follows: 내가 상대를 팔로우하는 문서만 만든다', async () => {
  await assertSucceeds(bob().doc('follows/bob_carol').set({ followerId: 'bob', followingId: 'carol', createdAt: 1 }));
});

test('follows: 남 대신 팔로우·자기 자신 팔로우·ID 불일치는 막는다', async () => {
  await assertFails(bob().doc('follows/alice_carol').set({ followerId: 'alice', followingId: 'carol' }));
  await assertFails(bob().doc('follows/bob_bob').set({ followerId: 'bob', followingId: 'bob' }));
  await assertFails(bob().doc('follows/bob_x').set({ followerId: 'bob', followingId: 'carol' }));
});

test('follows: 언팔로우는 팔로우한 본인, 팔로우 끊기는 팔로우당한 사람도 (6.3 차단)', async () => {
  await assertSucceeds(alice().doc('follows/bob_alice').delete());
});

test('follows: 관계없는 사람은 팔로우를 못 지운다', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('follows/alice_carol').set({ followerId: 'alice', followingId: 'carol' });
  });
  await assertFails(bob().doc('follows/alice_carol').delete());
  await assertSucceeds(alice().doc('follows/alice_carol').delete());
});

// ---------- blockedUsers (6.3) ----------

test('blockedUsers: 본인 차단 목록만 만들고 읽는다', async () => {
  await assertSucceeds(alice().doc('users/alice/blockedUsers/bob').set({ createdAt: 1 }));
  await assertSucceeds(alice().doc('users/alice/blockedUsers/bob').get());
  await assertFails(bob().doc('users/alice/blockedUsers/bob').get());
  await assertFails(bob().doc('users/alice/blockedUsers/carol').set({ createdAt: 1 }));
});

test('blockedUsers: 나를 차단한 사람은 팔로우할 수 없다', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc('users/carol/blockedUsers/bob').set({ createdAt: 1 });
  });
  await assertFails(bob().doc('follows/bob_carol').set({ followerId: 'bob', followingId: 'carol', createdAt: 1 }));
  // 내가 차단한 쪽이어도 규칙은 막지 않는다 (앱 화면에서 팔로우 버튼을 숨김)
  await assertSucceeds(env.authenticatedContext('carol').firestore()
    .doc('follows/carol_bob').set({ followerId: 'carol', followingId: 'bob', createdAt: 1 }));
});

// ---------- ratings ----------

test('ratings: 본인 평가를 만든다 (별점 1~5 정수, 공개 범위 2가지)', async () => {
  await assertSucceeds(bob().doc('ratings/bob_v1').set({
    userId: 'bob', videoId: 'v1', categoryId: '10', score: 5, comment: '',
    visibility: 'public', likeCount: 0, commentCount: 0,
  }));
});

test('ratings: 별점 범위 밖·남 이름·ID 불일치·숫자 미리 채우기는 막는다', async () => {
  const base = { userId: 'bob', videoId: 'v1', categoryId: '10', comment: '', visibility: 'public', likeCount: 0, commentCount: 0 };
  await assertFails(bob().doc('ratings/bob_v1').set({ ...base, score: 6 }));
  await assertFails(bob().doc('ratings/bob_v1').set({ ...base, score: 0 }));
  await assertFails(bob().doc('ratings/bob_v1').set({ ...base, score: 3.5 }));
  await assertFails(bob().doc('ratings/bob_v1').set({ ...base, score: 3, visibility: 'friends' }));
  await assertFails(bob().doc('ratings/alice_v3').set({ ...base, score: 3, userId: 'alice', videoId: 'v3' }));
  await assertFails(bob().doc('ratings/bob_v9').set({ ...base, score: 3 }));
  await assertFails(bob().doc('ratings/bob_v1').set({ ...base, score: 3, likeCount: 99 }));
});

test('ratings: 공개 평가는 누구나, 비공개 평가는 작성자만 읽는다', async () => {
  await assertSucceeds(bob().doc('ratings/alice_v1').get());
  await assertFails(bob().doc('ratings/alice_v2').get());
  await assertSucceeds(alice().doc('ratings/alice_v2').get());
});

test('ratings: 아직 없는 내 평가 문서는 "없음"으로 읽힌다 (영상 상세의 내 평가 불러오기)', async () => {
  const snap = await assertSucceeds(bob().doc('ratings/bob_v1').get());
  if (snap.exists) throw new Error('should not exist');
  await assertFails(guest().doc('ratings/bob_v1').get());
});

test('ratings: 다른 사람 평가는 공개 조건을 넣어 조회해야 한다', async () => {
  await assertSucceeds(bob().collection('ratings').where('userId', '==', 'alice').where('visibility', '==', 'public').get());
  await assertFails(bob().collection('ratings').where('userId', '==', 'alice').get());
  await assertSucceeds(alice().collection('ratings').where('userId', '==', 'alice').get());
  await assertSucceeds(bob().collection('ratings').where('videoId', '==', 'v1').where('visibility', '==', 'public').get());
});

test('ratings: 작성자는 별점·한줄평·공개 범위를 고친다', async () => {
  await assertSucceeds(alice().doc('ratings/alice_v1').update({ score: 5, comment: '최고', visibility: 'private', updatedAt: 1 }));
});

test('ratings: 남의 평가 내용은 못 고친다', async () => {
  await assertFails(bob().doc('ratings/alice_v1').update({ score: 1 }));
  await assertFails(bob().doc('ratings/alice_v1').update({ likeCount: 1, score: 1 }));
});

test('ratings: 누구나 공개 평가의 좋아요·댓글 수만 1씩 바꾼다', async () => {
  await assertSucceeds(bob().doc('ratings/alice_v1').update({ likeCount: 1 }));
  await assertSucceeds(bob().doc('ratings/alice_v1').update({ commentCount: 1 }));
  await assertFails(bob().doc('ratings/alice_v1').update({ likeCount: 5 }));
  await assertFails(bob().doc('ratings/alice_v2').update({ likeCount: 1 }));
});

test('ratings: 작성자는 자기 평가에 좋아요할 수 있다', async () => {
  await assertSucceeds(alice().doc('ratings/alice_v1').update({ likeCount: 1 }));
});

test('ratings: 삭제는 작성자만', async () => {
  await assertFails(bob().doc('ratings/alice_v1').delete());
  await assertSucceeds(alice().doc('ratings/alice_v1').delete());
});

test('ratings/likes: 본인 uid 좋아요 문서만', async () => {
  await assertSucceeds(bob().doc('ratings/alice_v1/likes/bob').set({ createdAt: 1 }));
  await assertFails(bob().doc('ratings/alice_v1/likes/alice').set({ createdAt: 1 }));
  await assertSucceeds(bob().doc('ratings/alice_v1/likes/bob').delete());
});

// ---------- comments ----------

test('comments: 공개 평가에 본인 이름으로 댓글을 쓴다', async () => {
  await assertSucceeds(bob().collection('ratings/alice_v1/comments').add({
    ratingId: 'alice_v1', userId: 'bob', authorName: '밥', authorUsername: 'bob',
    parentId: null, targetUsername: null, content: '동의해요', likeCount: 0, createdAt: 1,
  }));
});

test('comments: 남 이름·빈 내용·비공개 평가에는 못 쓴다', async () => {
  await assertFails(bob().collection('ratings/alice_v1/comments').add({ userId: 'alice', content: '사칭', likeCount: 0 }));
  await assertFails(bob().collection('ratings/alice_v1/comments').add({ userId: 'bob', content: '', likeCount: 0 }));
  await assertFails(bob().collection('ratings/alice_v2/comments').add({ userId: 'bob', content: '안녕', likeCount: 0 }));
});

test('comments: 공개 평가 댓글은 읽고, 남의 비공개 평가 댓글은 못 읽는다', async () => {
  await assertSucceeds(bob().collection('ratings/alice_v1/comments').get());
  await assertFails(bob().collection('ratings/alice_v2/comments').get());
});

test('comments: 누구나 댓글 좋아요 수만 1씩, 내용 수정·남의 댓글 삭제는 막는다', async () => {
  await assertSucceeds(bob().doc('ratings/alice_v1/comments/c1').update({ likeCount: 1 }));
  await assertFails(bob().doc('ratings/alice_v1/comments/c1').update({ content: '바꿈' }));
  await assertFails(bob().doc('ratings/alice_v1/comments/c1').delete());
  await assertSucceeds(alice().doc('ratings/alice_v1/comments/c1').delete());
});

test('comments/likes: 본인 uid 댓글 좋아요 문서만', async () => {
  await assertSucceeds(bob().doc('ratings/alice_v1/comments/c1/likes/bob').set({ createdAt: 1 }));
  await assertFails(bob().doc('ratings/alice_v1/comments/c1/likes/alice').set({ createdAt: 1 }));
});

// ---------- videos ----------

test('videos: 로그인한 사람은 정해진 영상 정보·집계 필드만 쓴다', async () => {
  await assertSucceeds(bob().doc('videos/v2').set({
    title: '새 영상', channelId: 'c', channelTitle: '채널', thumbnailUrl: 'https://i.ytimg.com/x.jpg',
    categoryId: '10', durationSeconds: 100, publishedAt: 1, fetchedAt: 2,
  }));
  await assertSucceeds(bob().doc('videos/v1').set({
    publicRatingCount: 1, ratingSum: 5, ratingAverage: 5, ratingDistribution: { s5: 1 }, statsUpdatedAt: 3,
  }, { merge: true }));
});

test('videos: 정해지지 않은 필드·삭제·로그인 안 한 읽기는 막는다', async () => {
  await assertFails(bob().doc('videos/v3').set({ title: 'x', hacked: true }));
  await assertFails(bob().doc('videos/v1').update({ hacked: true }));
  await assertFails(bob().doc('videos/v1').delete());
  await assertFails(guest().doc('videos/v1').get());
});

// ---------- categories ----------

test('categories: 읽기만 된다', async () => {
  await assertSucceeds(bob().doc('categories/10').get());
  await assertFails(bob().doc('categories/99').set({ name: '새 카테고리' }));
});

// ---------- 정의되지 않은 경로 ----------

test('규칙에 없는 컬렉션은 막는다', async () => {
  await assertFails(bob().doc('secrets/x').set({ a: 1 }));
  await assertFails(bob().doc('secrets/x').get());
});

// ---------- Storage ----------

const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]);

test('storage: 본인 프로필 사진(이미지)만 올린다', async () => {
  const storage = env.authenticatedContext('alice').storage();
  await assertSucceeds(storage.ref('profileImages/alice.jpg').put(jpeg, { contentType: 'image/jpeg' }));
  await assertFails(storage.ref('profileImages/bob.jpg').put(jpeg, { contentType: 'image/jpeg' }));
  await assertFails(storage.ref('profileImages/alice.jpg').put(jpeg, { contentType: 'text/plain' }));
  await assertFails(storage.ref('other/alice.jpg').put(jpeg, { contentType: 'image/jpeg' }));
});

test('storage: 2MB 이상은 막는다', async () => {
  const storage = env.authenticatedContext('alice').storage();
  const big = new Uint8Array(2 * 1024 * 1024 + 1);
  await assertFails(storage.ref('profileImages/alice.jpg').put(big, { contentType: 'image/jpeg' }));
});

test('storage: 로그인 안 하면 올리지 못한다', async () => {
  const storage = env.unauthenticatedContext().storage();
  await assertFails(storage.ref('profileImages/alice.jpg').put(jpeg, { contentType: 'image/jpeg' }));
});
