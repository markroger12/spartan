package dev.aegisac.paper.admin;
import com.github.retrooper.packetevents.*;
import com.github.retrooper.packetevents.protocol.player.User;
import dev.aegisac.paper.AegisPlugin;
import dev.aegisac.common.config.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@SuppressWarnings("deprecation")
class AdminMenuTest {
    ServerMock server; AegisPlugin plugin; PlayerMock staff,target;
    @BeforeEach void setup() {
        server=MockBukkit.mock(); MockBukkit.createMockPlugin("packetevents","2.14.0");
        var api=mock(PacketEventsAPI.class,RETURNS_DEEP_STUBS); when(api.isInitialized()).thenReturn(true);
        when(api.getPlayerManager().getUser(any(Player.class))).thenAnswer(call->{ Player p=call.getArgument(0); var u=mock(User.class); when(u.getUUID()).thenReturn(p.getUniqueId()); return u; });
        PacketEvents.setAPI(api); plugin=MockBukkit.load(AegisPlugin.class); staff=server.addPlayer("Staff"); target=server.addPlayer("Alice"); staff.setOp(true);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); PacketEvents.setAPI(null); }
    InventoryClickEvent click(int slot,ClickType type) {
        var e=new InventoryClickEvent(staff.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,type,InventoryAction.PICKUP_ALL,0);
        server.getPluginManager().callEvent(e); return e;
    }
    void tick() { server.getScheduler().performOneTick(); }
    void menu(String name) { plugin.menus().open(staff,name,null,0); }
    @Test void granularCommandsAndBypassManagementAreSeparateFromExemption() {
        staff.setOp(false);
        for(String action:List.of("debug","toggle","reset","exempt","bypass","freeze","unfreeze","top","gui")) {
            server.dispatchCommand(staff,"ac "+action+" Alice"); assertTrue(staff.nextMessage().contains("permission"),action);
        }
        staff.addAttachment(plugin,"aegisac.bypass",true);
        server.dispatchCommand(staff,"ac bypass Alice SpeedA 5m"); assertTrue(staff.nextMessage().contains("permission"));
        assertFalse(plugin.players().find(target.getUniqueId()).temporarilyExempt("SpeedA",System.nanoTime()));
        staff.addAttachment(plugin,"aegisac.bypass.manage",true);
        server.dispatchCommand(staff,"ac bypass Alice SpeedA 5m"); assertTrue(staff.nextMessage().contains("applied"));
        assertTrue(plugin.players().find(target.getUniqueId()).temporarilyExempt("SpeedA",System.nanoTime()));
        assertFalse(plugin.players().find(target.getUniqueId()).temporarilyExempt("FlyA",System.nanoTime()));
    }
    @Test void completionAndCategoryChecksAreFiltered() {
        staff.setOp(false); staff.addAttachment(plugin,"aegisac.toggle",true);
        assertEquals(List.of("toggle"),plugin.getCommand("ac").tabComplete(staff,"ac",new String[]{""}));
        assertEquals(List.of("SpeedA"),plugin.getCommand("ac").tabComplete(staff,"ac",new String[]{"toggle","speed"}));
        staff.addAttachment(plugin,"aegisac.checks",true); server.dispatchCommand(staff,"ac checks combat");
        int lines=0; String message; while((message=staff.nextMessage())!=null) { assertFalse(message.contains("SpeedA")); lines++; } assertEquals(15,lines);
        assertEquals(List.of("combat"),plugin.getCommand("ac").tabComplete(staff,"ac",new String[]{"checks","com"}));
    }
    @Test void allRequiredMenusOpenAndProfileHasBoundedObservations() {
        for(String name:GuiSettings.MENUS) {
            plugin.menus().open(staff,name,Set.of("profile","history").contains(name)?target.getUniqueId():null,0);
            assertEquals(54,staff.getOpenInventory().getTopInventory().getSize(),name);
            assertTrue(staff.getOpenInventory().getTitle().toLowerCase(Locale.ROOT).contains(name),name);
        }
        server.dispatchCommand(staff,"ac profile Alice"); var lines=new ArrayList<String>(); String line; while((line=staff.nextMessage())!=null) lines.add(line);
        assertTrue(lines.stream().anyMatch(s->s.contains("Transaction ping"))); assertTrue(lines.stream().anyMatch(s->s.contains("Environment"))); assertTrue(lines.size()<=24);
    }
    @ParameterizedTest @EnumSource(value=ClickType.class,names={"SHIFT_LEFT","SHIFT_RIGHT","NUMBER_KEY","DOUBLE_CLICK","DROP","CONTROL_DROP","SWAP_OFFHAND","MIDDLE","WINDOW_BORDER_LEFT"})
    void unusualClicksAreCancelledWithoutChanges(ClickType type) {
        menu("movement"); assertTrue(click(0,type).isCancelled()); tick(); assertEquals(1,plugin.configuration().current().generation());
    }
    @Test void bottomInventoryAndDragCannotTransferItems() {
        menu("movement"); assertTrue(click(54,ClickType.LEFT).isCancelled()); tick();
        var drag=new InventoryDragEvent(staff.getOpenInventory(),new ItemStack(Material.DIAMOND),new ItemStack(Material.DIAMOND,2),false,Map.of(0,new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag); assertTrue(drag.isCancelled()); assertEquals(1,plugin.configuration().current().generation());
    }
    @Test void itemNamesCannotSpoofActionsAndUnauthorizedViewerCannotToggle() {
        staff.setOp(false); staff.addAttachment(plugin,"aegisac.gui",true); staff.addAttachment(plugin,"aegisac.checks",true); menu("movement");
        var item=new ItemStack(Material.PAPER); var meta=item.getItemMeta(); meta.setDisplayName("Toggle SpeedA"); item.setItemMeta(meta);
        staff.getOpenInventory().getTopInventory().setItem(40,item); assertTrue(click(40,ClickType.LEFT).isCancelled()); tick();
        assertTrue(click(0,ClickType.LEFT).isCancelled()); tick(); assertEquals(1,plugin.configuration().current().generation());
    }
    @Test void validCheckTogglePersistsAndOldMenuCannotReplay() throws Exception {
        menu("movement"); var view=staff.getOpenInventory(); boolean before=CheckCatalog.enabled(plugin.configuration().current(),CheckCatalog.find("SpeedA"));
        click(0,ClickType.LEFT); tick(); awaitGeneration(2);
        assertEquals(!before,CheckCatalog.enabled(plugin.configuration().current(),CheckCatalog.find("SpeedA")));
        assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve("checks/movement.yml.last-admin-edit.bak")));
        var replay=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(replay); tick(); assertTrue(replay.isCancelled()); assertEquals(2,plugin.configuration().current().generation());
    }
    @Test void configurationReloadAndPermissionRevocationInvalidateOpenMenu() throws Exception {
        menu("movement"); plugin.configuration().reload(); click(0,ClickType.LEFT); tick(); assertEquals(2,plugin.configuration().current().generation());
        staff.setOp(false); staff.addAttachment(plugin,"aegisac.gui",true); staff.addAttachment(plugin,"aegisac.checks",true);
        var permission=staff.addAttachment(plugin,"aegisac.toggle",true); menu("movement"); click(0,ClickType.LEFT);
        staff.removeAttachment(permission); tick(); assertEquals(2,plugin.configuration().current().generation());
    }
    @Test void rightClickRequiresPunishmentPermissionIndependently() {
        staff.setOp(false); staff.addAttachment(plugin,"aegisac.gui",true); staff.addAttachment(plugin,"aegisac.checks",true); staff.addAttachment(plugin,"aegisac.toggle",true);
        menu("movement"); click(0,ClickType.RIGHT); tick(); assertFalse(plugin.configuration().current().output().punishments().rules().get("SpeedA").enabled()); assertEquals(1,plugin.configuration().current().generation());
    }
    @Test void resetMenuRejectsReplacedTargetSession() {
        plugin.menus().open(staff,"profile",target.getUniqueId(),0);
        plugin.players().quit(target.getUniqueId()); var fresh=plugin.players().join(target.getUniqueId(),target.getName(),System.nanoTime());
        click(49,ClickType.LEFT); tick(); assertEquals(0,fresh.lossEpoch());
    }
    @Test void freezeHoldsPositionAllowsRotationAndUnfreezeClearsExemption() {
        server.dispatchCommand(staff,"ac freeze Alice 30s"); var data=plugin.players().find(target.getUniqueId());
        assertTrue(data.temporarilyExempt("SpeedA",System.nanoTime()));
        Location from=target.getLocation(),to=from.clone().add(1,0,0); to.setYaw(45);
        var event=new PlayerMoveEvent(target,from,to); server.getPluginManager().callEvent(event);
        assertEquals(from.getX(),event.getTo().getX()); assertEquals(45,event.getTo().getYaw());
        server.dispatchCommand(staff,"ac unfreeze Alice"); assertFalse(data.temporarilyExempt("SpeedA",System.nanoTime()));
        assertThrows(IllegalArgumentException.class,()->AdminService.duration("11m",600)); assertThrows(IllegalArgumentException.class,()->AdminService.duration("0s",600));
    }
    @Test void disableClosesOwnedInventoriesAndClearsFreeze() {
        menu("dashboard"); plugin.admin().freeze(target,60_000_000_000L); server.getPluginManager().disablePlugin(plugin);
        assertTrue(staff.getOpenInventory().getTopInventory()==null||staff.getOpenInventory().getTopInventory().getSize()!=54); assertEquals(0,plugin.players().size());
    }
    @Test void additionalAliasPreservesConflictsDelegatesAndUnregistersOnlyOwnedCommands() throws Exception {
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),"config-version: 1\ncommand-aliases: {staffac: true, help: true}\n");
        // Alias values are restart-only; seed the service as a freshly loaded configuration.
        var field=AegisPlugin.class.getDeclaredField("configuration"); field.setAccessible(true);
        var service=new ConfigService(new ConfigurationLoader(plugin.getDataFolder().toPath())); service.reload(); field.set(plugin,service);
        server.getCommandMap().register("fixture",new org.bukkit.command.Command("help") {
            @Override public boolean execute(org.bukkit.command.CommandSender sender,String label,String[] args) { return true; }
        });
        var help=server.getCommandMap().getCommand("help"); var aliases=new CommandAliases(plugin,new dev.aegisac.paper.command.AegisCommand(plugin));
        assertNotNull(server.getCommandMap().getCommand("staffac")); assertSame(help,server.getCommandMap().getCommand("help"));
        server.dispatchCommand(staff,"staffac version"); assertTrue(staff.nextMessage().contains("Phase 10"));
        aliases.close(); assertNull(server.getCommandMap().getCommand("staffac")); assertSame(help,server.getCommandMap().getCommand("help"));
    }

    @Test void settingsAndPerCheckPunishmentEditsPersistWithSeparateControls() throws Exception {
        menu("settings"); click(51,ClickType.LEFT); tick(); awaitGeneration(2);
        assertEquals("strict",plugin.configuration().current().defaultProfile());
        menu("settings"); click(50,ClickType.LEFT); tick(); awaitGeneration(3);
        assertTrue(plugin.configuration().current().output().punishments().enabled());
        menu("movement"); click(0,ClickType.RIGHT); tick(); awaitGeneration(4);
        assertTrue(plugin.configuration().current().output().punishments().rules().get("SpeedA").enabled());
        plugin.menus().open(staff,"profile",target.getUniqueId(),0); click(51,ClickType.LEFT); tick(); awaitGeneration(5);
        assertEquals("lenient",plugin.configuration().current().worldProfiles().get(target.getWorld().getName()));
        assertEquals("strict",plugin.configuration().reload().defaultProfile());
    }
    @Test void closingMenuBeforeCommitRejectsQueuedEdit() throws Exception {
        menu("movement"); click(0,ClickType.LEFT); tick(); staff.closeInventory();
        long deadline=System.nanoTime()+10_000_000_000L; boolean rejected=false;
        while(System.nanoTime()<deadline&&!rejected) {
            tick(); String message; while((message=staff.nextMessage())!=null) if(message.contains("Edit rejected")) rejected=true;
            if(!rejected) Thread.sleep(5);
        }
        assertTrue(rejected); assertEquals(1,plugin.configuration().current().generation());
        assertFalse(Files.exists(plugin.getDataFolder().toPath().resolve("checks/movement.yml.last-admin-edit.bak")));
    }
    @Test void expiredFreezeAndTeleportReleaseControlWithoutRemovingIndependentExemption() {
        var d=plugin.players().find(target.getUniqueId()); d.exempt("SpeedA",System.nanoTime(),60_000_000_000L);
        plugin.admin().freeze(target,1); plugin.admin().tick(); assertFalse(d.temporarilyExempt("FlyA",System.nanoTime())); assertTrue(d.temporarilyExempt("SpeedA",System.nanoTime()));
        plugin.admin().freeze(target,60_000_000_000L);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerTeleportEvent(target,target.getLocation(),target.getLocation().add(1,0,0)));
        assertFalse(d.temporarilyExempt("FlyA",System.nanoTime())); assertTrue(d.temporarilyExempt("SpeedA",System.nanoTime()));
    }

    @Test void repeatedClicksScheduleAtMostOneActionPerMenuPerTick() {
        menu("movement"); int before=server.getScheduler().getPendingTasks().size();
        for(int i=0;i<100;i++) assertTrue(click(0,ClickType.LEFT).isCancelled());
        assertEquals(before+1,server.getScheduler().getPendingTasks().size());
        staff.closeInventory(); tick(); assertEquals(1,plugin.configuration().current().generation());
    }
    void awaitGeneration(long expected) throws Exception {
        long deadline=System.nanoTime()+10_000_000_000L;
        while(System.nanoTime()<deadline) { tick(); if(plugin.configuration().current().generation()==expected) { tick(); return; } Thread.sleep(5); }
        var messages=new ArrayList<String>(); String message; while((message=staff.nextMessage())!=null) messages.add(message);
        fail("Administrative transaction did not publish generation "+expected+" messages="+messages);
    }
}
