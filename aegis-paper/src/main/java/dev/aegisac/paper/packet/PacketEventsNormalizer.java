package dev.aegisac.paper.packet;

import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import dev.aegisac.common.packet.BrandDecoder;
import dev.aegisac.common.packet.NormalizedPacket;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.packet.PacketKind;
import java.util.EnumSet;
import java.util.function.ToIntFunction;

/** No Bukkit access, packet retention, item/NBT copies or per-packet scheduling. */
public final class PacketEventsNormalizer implements PacketNormalizer {
    private static final EnumSet<PacketType.Play.Client> CLIENT = EnumSet.of(
            PacketType.Play.Client.PLAYER_FLYING, PacketType.Play.Client.PLAYER_POSITION,
            PacketType.Play.Client.PLAYER_ROTATION, PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION,
            PacketType.Play.Client.KEEP_ALIVE, PacketType.Play.Client.PONG, PacketType.Play.Client.WINDOW_CONFIRMATION,
            PacketType.Play.Client.TELEPORT_CONFIRM, PacketType.Play.Client.INTERACT_ENTITY,
            PacketType.Play.Client.PLAYER_DIGGING, PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT,
            PacketType.Play.Client.USE_ITEM, PacketType.Play.Client.HELD_ITEM_CHANGE,
            PacketType.Play.Client.CLICK_WINDOW, PacketType.Play.Client.CLICK_WINDOW_BUTTON,
            PacketType.Play.Client.CLOSE_WINDOW, PacketType.Play.Client.ENTITY_ACTION,
            PacketType.Play.Client.PLAYER_ABILITIES, PacketType.Play.Client.VEHICLE_MOVE,
            PacketType.Play.Client.ANIMATION, PacketType.Play.Client.CLIENT_STATUS,
            PacketType.Play.Client.PLAYER_INPUT, PacketType.Play.Client.STEER_VEHICLE,
            PacketType.Play.Client.PLUGIN_MESSAGE);
    private static final EnumSet<PacketType.Play.Server> SERVER = EnumSet.of(
            PacketType.Play.Server.SPAWN_ENTITY, PacketType.Play.Server.SPAWN_PLAYER,
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE, PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION,
            PacketType.Play.Server.ENTITY_TELEPORT, PacketType.Play.Server.ENTITY_POSITION_SYNC,
            PacketType.Play.Server.DESTROY_ENTITIES, PacketType.Play.Server.ENTITY_METADATA,
            PacketType.Play.Server.UPDATE_ATTRIBUTES,
            PacketType.Play.Server.KEEP_ALIVE, PacketType.Play.Server.PING, PacketType.Play.Server.WINDOW_CONFIRMATION,
            PacketType.Play.Server.PLAYER_POSITION_AND_LOOK, PacketType.Play.Server.PLAYER_ROTATION,
            PacketType.Play.Server.ENTITY_VELOCITY, PacketType.Play.Server.EXPLOSION,
            PacketType.Play.Server.ENTITY_EFFECT, PacketType.Play.Server.REMOVE_ENTITY_EFFECT,
            PacketType.Play.Server.ENTITY_STATUS, PacketType.Play.Server.PLAYER_ABILITIES,
            PacketType.Play.Server.HELD_ITEM_CHANGE, PacketType.Play.Server.OPEN_WINDOW,
            PacketType.Play.Server.CLOSE_WINDOW, PacketType.Play.Server.WINDOW_ITEMS,
            PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.ACKNOWLEDGE_BLOCK_CHANGES,
            PacketType.Play.Server.VEHICLE_MOVE);
    private final int maximumBytes;
    private final ToIntFunction<Object> size;
    public PacketEventsNormalizer(int maximumBytes) { this(maximumBytes, ByteBufHelper::readableBytes); }
    public PacketEventsNormalizer(int maximumBytes, ToIntFunction<Object> size) {
        this.maximumBytes = maximumBytes; this.size = size;
    }
    @Override public NormalizedPacket normalize(ProtocolPacketEvent event) {
        if (event instanceof PacketReceiveEvent receive && event.getPacketType() instanceof PacketType.Play.Client type) {
            if (!CLIENT.contains(type)) return Other.INSTANCE;
            return inbound(receive, type, bounded(event));
        }
        if (event instanceof PacketSendEvent send && event.getPacketType() instanceof PacketType.Play.Server type) {
            // World reset packets can contain large dimension registries; do not decode them here.
            if (type == PacketType.Play.Server.RESPAWN || type == PacketType.Play.Server.JOIN_GAME)
                return new Action(PacketKind.WORLD_RESET, type.name());
            if (type == PacketType.Play.Server.CHUNK_DATA || type == PacketType.Play.Server.UNLOAD_CHUNK
                    || type == PacketType.Play.Server.BLOCK_CHANGE || type == PacketType.Play.Server.MULTI_BLOCK_CHANGE
                    || type == PacketType.Play.Server.BLOCK_ACTION)
                return new Action(PacketKind.WORLD_CHANGE, type.name());
            if (!SERVER.contains(type)) return Other.INSTANCE;
            bounded(event);
            return outbound(send, type);
        }
        return Other.INSTANCE;
    }
    private int bounded(ProtocolPacketEvent event) {
        int bytes = size.applyAsInt(event.getByteBuf());
        if (bytes < 0 || bytes > maximumBytes) throw new PacketDecodeException("Packet exceeds analysis byte budget");
        return bytes;
    }
    private NormalizedPacket inbound(PacketReceiveEvent event, PacketType.Play.Client type, int bytes) {
        if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            var packet = new WrapperPlayClientPlayerFlying(event);
            var location = packet.getLocation();
            return new Movement(packet.hasPositionChanged(), packet.hasRotationChanged(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(), packet.isOnGround(), packet.isHorizontalCollision());
        }
        return switch (type) {
            case KEEP_ALIVE -> new Timing(TimingKind.KEEP_ALIVE, new WrapperPlayClientKeepAlive(event).getId(), 0, true);
            case PONG -> new Timing(TimingKind.PING, new WrapperPlayClientPong(event).getId(), 0, true);
            case WINDOW_CONFIRMATION -> {
                var packet = new WrapperPlayClientWindowConfirmation(event);
                yield new Timing(TimingKind.WINDOW, packet.getActionId(), packet.getWindowId(), packet.isAccepted());
            }
            case TELEPORT_CONFIRM -> new Timing(TimingKind.TELEPORT, new WrapperPlayClientTeleportConfirm(event).getTeleportId(), 0, true);
            case INTERACT_ENTITY -> {
                var packet = new WrapperPlayClientInteractEntity(event);
                var target = packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT ? packet.getLocation() : null;
                yield new Interaction(packet.getEntityId(), packet.getAction().name(), packet.getHand() == null ? "UNKNOWN" : packet.getHand().name(),
                        target != null, target == null ? 0 : target.getX(), target == null ? 0 : target.getY(), target == null ? 0 : target.getZ());
            }
            case PLAYER_DIGGING -> {
                var packet = new WrapperPlayClientPlayerDigging(event); var pos = packet.getBlockPosition();
                yield new BlockAction(false, pos.getX(), pos.getY(), pos.getZ(), packet.getBlockFaceId(), packet.getSequence(),
                        packet.getAction().name(), "", 0, 0, 0);
            }
            case PLAYER_BLOCK_PLACEMENT -> {
                var packet = new WrapperPlayClientPlayerBlockPlacement(event); var pos = packet.getBlockPosition(); var cursor = packet.getCursorPosition();
                yield new BlockAction(true, pos.getX(), pos.getY(), pos.getZ(), packet.getFaceId(), packet.getSequence(), "PLACE",
                        packet.getHand().name(), cursor.getX(), cursor.getY(), cursor.getZ());
            }
            case USE_ITEM -> {
                var packet = new WrapperPlayClientUseItem(event);
                yield new UseItem(packet.getHand().name(), packet.getSequence(), packet.getYaw(), packet.getPitch());
            }
            case HELD_ITEM_CHANGE -> new HeldItem(new WrapperPlayClientHeldItemChange(event).getSlot());
            case CLICK_WINDOW -> {
                var packet = new WrapperPlayClientClickWindow(event);
                yield new Inventory(InventoryAction.CLICK, packet.getWindowId(), packet.getStateId().orElse(-1),
                        packet.getSlot(), packet.getButton(), packet.getWindowClickType().name());
            }
            case CLICK_WINDOW_BUTTON -> {
                var packet = new WrapperPlayClientClickWindowButton(event);
                yield new Inventory(InventoryAction.BUTTON, packet.getWindowId(), -1, -1, packet.getButtonId(), "");
            }
            case CLOSE_WINDOW -> new Inventory(InventoryAction.CLOSE, new WrapperPlayClientCloseWindow(event).getWindowId(), -1, -1, -1, "");
            case ENTITY_ACTION -> {
                var packet = new WrapperPlayClientEntityAction(event);
                yield new EntityAction(packet.getEntityId(), packet.getAction().name(), packet.getJumpBoost());
            }
            case PLAYER_ABILITIES -> new Abilities(new WrapperPlayClientPlayerAbilities(event).isFlying(), false, false, false, 0, 0);
            case VEHICLE_MOVE -> {
                var packet = new WrapperPlayClientVehicleMove(event); var pos = packet.getPosition();
                yield new Vehicle(pos.getX(), pos.getY(), pos.getZ(), packet.getYaw(), packet.getPitch());
            }
            case ANIMATION -> new Action(PacketKind.SWING, new WrapperPlayClientAnimation(event).getHand().name());
            case CLIENT_STATUS -> new Action(PacketKind.CLIENT_STATUS, new WrapperPlayClientClientStatus(event).getAction().name());
            case PLAYER_INPUT -> {
                var packet = new WrapperPlayClientPlayerInput(event);
                int mask = (packet.isForward() ? 1 : 0) | (packet.isBackward() ? 2 : 0)
                        | (packet.isLeft() ? 4 : 0) | (packet.isRight() ? 8 : 0);
                yield new Input((packet.isForward() ? 1 : 0) - (packet.isBackward() ? 1 : 0),
                        (packet.isLeft() ? 1 : 0) - (packet.isRight() ? 1 : 0),
                        packet.isJump(), packet.isShift(), packet.isSprint(), false, mask);
            }
            case STEER_VEHICLE -> {
                var packet = new WrapperPlayClientSteerVehicle(event);
                yield new Input(packet.getForward(), packet.getSideways(), packet.isJump(), false, false, packet.isUnmount(), 0);
            }
            case PLUGIN_MESSAGE -> {
                var packet = new WrapperPlayClientPluginMessage(event);
                String channel = packet.getChannelName();
                if (channel.length() > 128) throw new PacketDecodeException("Payload channel exceeds analysis budget");
                boolean brand = channel.equals("minecraft:brand") || channel.equals("MC|Brand");
                yield new Payload(bytes, channel, brand ? BrandDecoder.decode(packet.getData()) : "");
            }
            default -> Other.INSTANCE;
        };
    }
    private NormalizedPacket outbound(PacketSendEvent event, PacketType.Play.Server type) {
        return switch (type) {
            case SPAWN_ENTITY -> {
                var p=new WrapperPlayServerSpawnEntity(event); var pos=p.getPosition();
                yield new EntitySpawn(p.getEntityId(),p.getUUID().orElse(null),
                        p.getEntityType()==com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.PLAYER,pos.getX(),pos.getY(),pos.getZ());
            }
            case SPAWN_PLAYER -> {
                var p=new WrapperPlayServerSpawnPlayer(event); var pos=p.getPosition();
                yield new EntitySpawn(p.getEntityId(),p.getUUID(),true,pos.getX(),pos.getY(),pos.getZ());
            }
            case ENTITY_RELATIVE_MOVE -> {
                var p=new WrapperPlayServerEntityRelativeMove(event);
                yield new EntityMove(p.getEntityId(),true,false,0,p.getDeltaX(),p.getDeltaY(),p.getDeltaZ());
            }
            case ENTITY_RELATIVE_MOVE_AND_ROTATION -> {
                var p=new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                yield new EntityMove(p.getEntityId(),true,false,0,p.getDeltaX(),p.getDeltaY(),p.getDeltaZ());
            }
            case ENTITY_TELEPORT -> {
                var p=new WrapperPlayServerEntityTeleport(event); var pos=p.getPosition();
                yield new EntityMove(p.getEntityId(),false,true,p.getRelativeFlags().getFullMask(),pos.getX(),pos.getY(),pos.getZ());
            }
            case ENTITY_POSITION_SYNC -> {
                var p=new WrapperPlayServerEntityPositionSync(event); var pos=p.getPosition().getEndPosition();
                yield new EntityMove(p.getId(),false,false,0,pos.getX(),pos.getY(),pos.getZ());
            }
            case DESTROY_ENTITIES -> {
                var ids=new WrapperPlayServerDestroyEntities(event).getEntityIds();
                if(ids.length>1024) throw new PacketDecodeException("Entity removal budget exceeded");
                yield new EntityRemove(java.util.Arrays.stream(ids).boxed().toList());
            }
            case ENTITY_METADATA -> new EntityDimensionsUnknown(new WrapperPlayServerEntityMetadata(event).getEntityId());
            case UPDATE_ATTRIBUTES -> new EntityDimensionsUnknown(new WrapperPlayServerUpdateAttributes(event).getEntityId());
            case KEEP_ALIVE -> new Timing(TimingKind.KEEP_ALIVE, new WrapperPlayServerKeepAlive(event).getId(), 0, true);
            case PING -> new Timing(TimingKind.PING, new WrapperPlayServerPing(event).getId(), 0, true);
            case WINDOW_CONFIRMATION -> {
                var packet = new WrapperPlayServerWindowConfirmation(event);
                yield new Timing(TimingKind.WINDOW, packet.getActionId(), packet.getWindowId(), packet.isAccepted());
            }
            case PLAYER_POSITION_AND_LOOK -> {
                var packet = new WrapperPlayServerPlayerPositionAndLook(event);
                var pos = packet.getPosition(); var delta = packet.getDeltaMovement();
                yield new Teleport(packet.getTeleportId(), pos.getX(), pos.getY(), pos.getZ(), packet.getYaw(), packet.getPitch(),
                        packet.getRelativeFlags().getFullMask(), delta.getX(), delta.getY(), delta.getZ());
            }
            case PLAYER_ROTATION -> {
                var packet = new WrapperPlayServerPlayerRotation(event);
                yield new RotationCorrection(packet.getYaw(), packet.getPitch(), packet.isRelativeYaw(), packet.isRelativePitch());
            }
            case ENTITY_VELOCITY -> {
                var packet = new WrapperPlayServerEntityVelocity(event); var v = packet.getVelocity();
                yield new Impulse(false, packet.getEntityId(), v.getX(), v.getY(), v.getZ());
            }
            case EXPLOSION -> {
                var v = new WrapperPlayServerExplosion(event).getKnockback();
                yield new Impulse(true, -1, v == null ? 0 : v.getX(), v == null ? 0 : v.getY(), v == null ? 0 : v.getZ());
            }
            case ENTITY_EFFECT -> {
                var packet = new WrapperPlayServerEntityEffect(event);
                yield new Effect(packet.getEntityId(), packet.getPotionType().getName().toString(), packet.getEffectAmplifier(), packet.getEffectDurationTicks(), false);
            }
            case REMOVE_ENTITY_EFFECT -> {
                var packet = new WrapperPlayServerRemoveEntityEffect(event);
                yield new Effect(packet.getEntityId(), packet.getPotionType().getName().toString(), 0, 0, true);
            }
            case ENTITY_STATUS -> {
                var packet = new WrapperPlayServerEntityStatus(event); yield new EntityStatus(packet.getEntityId(), packet.getStatus());
            }
            case PLAYER_ABILITIES -> {
                var packet = new WrapperPlayServerPlayerAbilities(event);
                yield new Abilities(packet.isFlying(), true, packet.isFlightAllowed(), packet.isInCreativeMode(), packet.getFlySpeed(), packet.getFOVModifier());
            }
            case HELD_ITEM_CHANGE -> new HeldItem(new WrapperPlayServerHeldItemChange(event).getSlot());
            case OPEN_WINDOW -> new Inventory(InventoryAction.OPEN, new WrapperPlayServerOpenWindow(event).getContainerId(), -1, -1, -1, "");
            case CLOSE_WINDOW -> new Inventory(InventoryAction.CLOSE, new WrapperPlayServerCloseWindow(event).getWindowId(), -1, -1, -1, "");
            case WINDOW_ITEMS -> {
                var packet = new WrapperPlayServerWindowItems(event);
                yield new Inventory(InventoryAction.CONTENTS, packet.getWindowId(), packet.getStateId(), -1, -1, "");
            }
            case SET_SLOT -> {
                var packet = new WrapperPlayServerSetSlot(event);
                yield new Inventory(InventoryAction.SLOT, packet.getWindowId(), packet.getStateId(), packet.getSlot(), -1, "");
            }
            case ACKNOWLEDGE_BLOCK_CHANGES -> new BlockAcknowledgement(new WrapperPlayServerAcknowledgeBlockChanges(event).getSequence());
            case VEHICLE_MOVE -> {
                var packet = new WrapperPlayServerVehicleMove(event); var pos = packet.getPosition();
                yield new Vehicle(pos.getX(), pos.getY(), pos.getZ(), packet.getYaw(), packet.getPitch());
            }
            default -> Other.INSTANCE;
        };
    }
}
