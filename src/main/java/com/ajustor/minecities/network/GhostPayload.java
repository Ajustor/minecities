package com.ajustor.minecities.network;

import com.ajustor.minecities.MineCities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le fantôme d'un bâtiment proposé (brief 09) : les blocs que le joueur {@code owner} s'apprête à
 * commander, que le client dessine en transparence. Une liste vide efface son fantôme. Un chantier en
 * cours ({@code site}, MC-23) se montre de même à qui tient le plan, sous l'identifiant du chantier,
 * avec les seuls blocs qui restent à poser.
 */
public record GhostPayload(UUID owner, boolean site, List<Block> blocks) implements CustomPacketPayload {
	public static final Type<GhostPayload> TYPE = new Type<>(MineCities.id("ghost"));

	/** Un bloc du fantôme. */
	public record Block(BlockPos pos, BlockState state) {
	}

	public static final StreamCodec<FriendlyByteBuf, GhostPayload> CODEC = StreamCodec.of(GhostPayload::write,
			GhostPayload::read);

	public static GhostPayload clear(UUID owner) {
		return new GhostPayload(owner, false, List.of());
	}

	private static void write(FriendlyByteBuf buf, GhostPayload payload) {
		buf.writeUUID(payload.owner());
		buf.writeBoolean(payload.site());
		buf.writeVarInt(payload.blocks().size());
		for (Block block : payload.blocks()) {
			buf.writeLong(block.pos().asLong());
			buf.writeVarInt(net.minecraft.world.level.block.Block.getId(block.state()));
		}
	}

	private static GhostPayload read(FriendlyByteBuf buf) {
		UUID owner = buf.readUUID();
		boolean site = buf.readBoolean();
		int count = buf.readVarInt();
		List<Block> blocks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			BlockPos pos = BlockPos.of(buf.readLong());
			blocks.add(new Block(pos, net.minecraft.world.level.block.Block.stateById(buf.readVarInt())));
		}
		return new GhostPayload(owner, site, blocks);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
