package com.hmed.enchantmentworkbench.network;

import com.hmed.enchantmentworkbench.EnchantmentWorkbenchMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public record EnchantRequestPayload(Map<Identifier, Integer> selections) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EnchantRequestPayload> TYPE =
		new CustomPacketPayload.Type<>(EnchantmentWorkbenchMod.id("enchant_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EnchantRequestPayload> STREAM_CODEC = StreamCodec.of(
		(buffer, payload) -> {
			buffer.writeInt(payload.selections().size());
			payload.selections().forEach((id, level) -> {
				buffer.writeIdentifier(id);
				buffer.writeInt(level);
			});
		},
		buffer -> {
			int size = buffer.readInt();
			Map<Identifier, Integer> selections = new LinkedHashMap<>();
			for (int i = 0; i < size; i++) {
				selections.put(buffer.readIdentifier(), buffer.readInt());
			}
			return new EnchantRequestPayload(selections);
		}
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
