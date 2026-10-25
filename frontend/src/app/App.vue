<template>
  <main class="welcome">
    <img class="welcome__brand" src="/favicon.svg" alt="" width="48" height="48" />
    <p class="welcome__eyebrow">校园自习室</p>
    <h1>为下一段专注，<br />找到一个好位置。</h1>
    <p class="welcome__description">查询校园自习空间，安排你的学习时段。</p>
  </main>
</template>

<style scoped>
.welcome {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  width: min(100%, 960px);
  margin-inline: auto;
  padding: 64px 32px;
}

.welcome__brand {
  margin-bottom: 28px;
}

.welcome__eyebrow {
  margin: 0 0 16px;
  color: #147d82;
  font-size: 15px;
  font-weight: 600;
}

h1 {
  margin: 0;
  font-size: clamp(32px, 5.5vw, 56px);
  font-weight: 600;
  letter-spacing: -0.035em;
  line-height: 1.35;
}

.welcome__description {
  margin-top: 24px;
  color: #597174;
  font-size: 16px;
  line-height: 1.8;
}
</style>
