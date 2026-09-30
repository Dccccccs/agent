package com.example.agentgateway.interfaces.filteredstream.metadata;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 最终 Metadata 首帧 body 的可插拔定制器。
 *
 * <p>外部 API 的 content 已经在进入本接口前转换为 ObjectNode。实现类可以：</p>
 * <ul>
 *     <li>使用 {@code body.put/set(...)} 增加或覆盖字段；</li>
 *     <li>使用 {@code body.remove(...)} 删除不对外暴露的字段；</li>
 *     <li>读取已有字段并计算新的输出字段。</li>
 * </ul>
 *
 * <p>定制器只修改 body；外层 returnType 由接口装配器固定为 METADATA。
 * 可以声明多个 Spring Bean，Spring 会按照 {@code @Order} 顺序依次执行。</p>
 */
@FunctionalInterface
public interface MetadataOutputCustomizer {
    void customize(ObjectNode body);
}
