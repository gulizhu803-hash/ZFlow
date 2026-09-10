<script setup>
import { computed } from 'vue';

const props = defineProps({
    layout: {
        type: String,
        default: 'app'
    },
    wrapperClass: {
        type: String,
        default: ''
    },
    innerClass: {
        type: String,
        default: ''
    }
});

const currentYear = new Date().getFullYear();

const wrapperByLayout = computed(() => {
    if (props.layout === 'admin') {
        return 'bg-white border-[#e2e8f0] backdrop-blur-0';
    }
    if (props.layout === 'auth') {
        return 'bg-transparent border-[rgba(15,23,42,0.06)] backdrop-blur-0';
    }
    return 'bg-[var(--surface-3)] border-[rgba(15,23,42,0.06)] backdrop-blur-[6px]';
});

const innerByLayout = computed(() => {
    if (props.layout === 'admin') {
        return 'px-6 text-[#64748b]';
    }
    if (props.layout === 'auth') {
        return 'px-[16px]';
    }
    return 'max-w-[var(--footer-content-max)] pl-[var(--footer-pad-x)] pr-[calc(var(--footer-pad-x)+var(--scrollbar-w))] max-[720px]:pl-[var(--footer-pad-x-mobile)] max-[720px]:pr-[calc(var(--footer-pad-x-mobile)+var(--scrollbar-w))]';
});
</script>

<template>
    <footer
        :class="[
            'h-[var(--footer-height)] w-full border-t',
            wrapperByLayout,
            wrapperClass
        ]"
    >
        <div
            :class="[
                'mx-auto grid h-full w-full grid-cols-3 items-center gap-[12px] text-[12px] text-[var(--text-secondary)]',
                innerByLayout,
                innerClass
            ]"
        >
            <span class="min-w-0 truncate" title="知流 ZFlow，由 zgl 基于 Dasi MiniAgent 二次开发">
                知流 ZFlow · zgl
            </span>
            <span class="min-w-0 truncate text-center" :title="`原作：Copyright © ${currentYear} Dasi. All Rights Reserved.`">
                原作 © {{ currentYear }} Dasi
            </span>
            <span class="min-w-0 truncate text-right" title="本网站内容由 AI 生成，仅供参考，请注意甄别。">
                本网站内容由 AI 生成，仅供参考，请注意甄别。
            </span>
        </div>
    </footer>
</template>
