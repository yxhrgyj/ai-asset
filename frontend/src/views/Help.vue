<template>
  <div class="portal-page portal-info-page portal-container">
    <section class="portal-page-intro">
      <div>
        <p class="portal-eyebrow"><span class="portal-eyebrow-line"></span> USING THE LIBRARY</p>
        <h1>帮助中心</h1>
        <p>从找到一项资产，到把它带回你的工作流，几个常见问题都在这里。</p>
      </div>
      <span class="portal-page-index">HELP / 01</span>
    </section>

    <section class="portal-help-layout">
      <div class="portal-help-aside"><span class="portal-side-label">QUICK START</span><h2>三步找到可用内容</h2><p>公开目录可以匿名浏览。下载和导出需要登录，以便记录取用并保护资产来源。</p></div>
      <ol class="portal-help-steps">
        <li><span>01</span><div><strong>浏览目录</strong><p>按关键词、类型或适用范围筛选，打开资产详情查看正文、版本和随附文件。</p></div></li>
        <li><span>02</span><div><strong>确认版本</strong><p>详情页默认显示最新已发布版本，也可以切换到历史版本进行对照。</p></div></li>
        <li><span>03</span><div><strong>登录并下载</strong><p>点击下载资产，登录后系统会记录这次取用并生成带版本信息的导出包。</p></div></li>
      </ol>
    </section>

    <section class="portal-faq-section">
      <div class="portal-section-heading"><div><span class="portal-side-label">FAQ</span><h2>常见问题</h2></div><RouterLink to="/assets-public" class="portal-text-link">去资产库 <span aria-hidden="true">→</span></RouterLink></div>
      <div class="portal-faq-list">
        <details open><summary>为什么有些资产能看但不能直接下载？</summary><p>公开目录允许匿名浏览已发布内容；下载需要身份确认，系统才能统计取用并在管理侧保留审计记录。</p></details>
        <details><summary>详情页显示的是哪个版本？</summary><p>默认显示最新的已发布版本。资产如果存在多个公开版本，可以在详情页的版本记录中切换查看。</p></details>
        <details><summary>下载包里包含什么？</summary><p>导出包包含当前版本正文和该版本的随附文件，并带有资产名称、版本号和内容出处信息。</p></details>
        <details><summary>如何提交或更新一项资产？</summary><p>请先登录管理中心。具备作者权限的成员可以创建草稿、补充内容并提交发布流程。</p></details>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>
