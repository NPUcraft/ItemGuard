'use strict';

const mineflayer = require('mineflayer');
const Vec3 = require('vec3');
const { waitUntil, sleep } = require('./util');

async function connectBot({ host, port, username, version }) {
  const bot = mineflayer.createBot({
    host,
    port,
    username,
    auth: 'offline',
    version,
    hideErrors: false,
    checkTimeoutInterval: 30000,
    respawn: true
  });

  const failure = new Promise((_, reject) => {
    bot.on('error', (error) => reject(error));
    bot.on('kicked', (reason) => reject(new Error(`Bot kicked: ${stringify(reason)}`)));
  });

  await Promise.race([
    once(bot, 'spawn'),
    failure,
    timeout(25000, 'Mineflayer spawn timed out')
  ]);
  if (typeof bot.waitForChunksToLoad === 'function') {
    try {
      await bot.waitForChunksToLoad();
    } catch {
      // continue; fixture teleport still places the bot
    }
  }
  await sleep(250);
  return bot;
}

async function disconnectBot(bot) {
  if (!bot) {
    return;
  }
  try {
    bot.quit('integration-complete');
  } catch {
    try {
      bot.end();
    } catch {
      // already gone
    }
  }
}

function countItem(bot, name) {
  const key = String(name).toLowerCase();
  return bot.inventory.items().reduce((sum, item) => sum + (item.name === key ? item.count : 0), 0);
}

async function openBlock(bot, x, y, z) {
  const target = new Vec3(x, y, z);
  const loaded = await waitUntil(() => {
    const block = bot.blockAt(target);
    return block && !String(block.name).includes('air') ? block : null;
  }, { timeout: 8000, interval: 100, message: `No block at ${x},${y},${z} (chunks not loaded?)` });
  if (!/chest|shulker|barrel|ender|furnace|blast|smoker|stonecutter/i.test(loaded.name)) {
    throw new Error(`Expected container at ${x},${y},${z}, Mineflayer saw ${loaded.name}`);
  }
  await bot.lookAt(loaded.position.offset(0.5, 0.5, 0.5), true);
  await sleep(150);
  let lastError = null;
  for (let attempt = 0; attempt < 3; attempt++) {
    const block = bot.blockAt(target) || loaded;
    try {
      if (typeof bot.openContainer === 'function') {
        return await bot.openContainer(block);
      }
    } catch (error) {
      lastError = error;
    }
    try {
      const opened = waitForEventOnce(bot, 'windowOpen', 4000);
      bot.activateBlock(block);
      return await opened;
    } catch (error) {
      lastError = error;
      await sleep(250);
    }
  }
  throw lastError || new Error(`Could not open container at ${x},${y},${z}`);
}

function waitForEventOnce(emitter, event, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      emitter.removeListener(event, onEvent);
      reject(new Error(`Event ${event} did not fire within ${timeoutMs}ms`));
    }, timeoutMs);
    const onEvent = (...args) => {
      clearTimeout(timer);
      resolve(args[0]);
    };
    emitter.once(event, onEvent);
  });
}

async function shiftClickSlot(bot, slot) {
  await bot.clickWindow(slot, 0, 1);
}

async function normalWithdrawSlot(bot, slot) {
  await bot.clickWindow(slot, 0, 0);
  const dest = firstEmptyWindowSlot(bot);
  if (dest == null) {
    throw new Error('No empty destination slot for normal click withdraw');
  }
  await bot.clickWindow(dest, 0, 0);
}

function firstEmptyWindowSlot(bot) {
  const window = bot.currentWindow || bot.inventory;
  const start = window.inventoryStart != null ? window.inventoryStart : 27;
  const end = window.inventoryEnd != null ? window.inventoryEnd : window.slots.length;
  for (let slot = start; slot < end; slot++) {
    if (!window.slots[slot]) {
      return slot;
    }
  }
  return null;
}

function firstInventorySlot(bot, name) {
  const item = bot.inventory.items().find((item) => item.name === String(name).toLowerCase());
  return item ? item.slot : null;
}

function firstEmptyInventorySlot(bot) {
  for (let slot = 9; slot <= 44; slot++) {
    if (!bot.inventory.slots[slot]) {
      return slot;
    }
  }
  return null;
}

async function swapInventorySlots(bot, from, to) {
  await bot.clickWindow(from, 0, 0);
  await bot.clickWindow(to, 0, 0);
}

async function numberKeySwap(bot, slot, hotbarIndex) {
  await bot.clickWindow(slot, hotbarIndex, 2);
}

async function doubleClickSlot(bot, slot) {
  await bot.clickWindow(slot, 0, 6);
}

async function dragFromCursorToSlots(bot, slots) {
  if (!Array.isArray(slots) || slots.length === 0) {
    throw new Error('dragFromCursorToSlots requires destination slots');
  }
  await bot.clickWindow(-999, 0, 5);
  for (const slot of slots) {
    await bot.clickWindow(slot, 1, 5);
  }
  await bot.clickWindow(-999, 2, 5);
}

async function craftDiamonds(bot, tableX, tableY, tableZ, count = 1) {
  const table = bot.blockAt(new Vec3(tableX, tableY, tableZ));
  if (!table) {
    throw new Error('Crafting table not loaded');
  }
  const diamondId = bot.registry.itemsByName.diamond.id;
  const recipes = bot.recipesFor(diamondId, null, 1, table);
  if (!recipes.length) {
    throw new Error('Mineflayer found no diamond uncraft recipe');
  }
  await bot.craft(recipes[0], count, table);
}

async function openVillagerByUuid(bot, uuid) {
  const entity = Object.values(bot.entities).find((candidate) => (
    candidate && String(candidate.uuid || '').replace(/-/g, '') === String(uuid || '').replace(/-/g, '')
  ));
  if (!entity) {
    throw new Error('Mineflayer could not see the merchant villager');
  }
  if (typeof bot.openVillager !== 'function') {
    throw new Error('Mineflayer openVillager is unavailable');
  }
  return bot.openVillager(entity);
}

async function tradeFirstOffer(villagerWindow, count = 1) {
  if (!villagerWindow || typeof villagerWindow.trade !== 'function') {
    throw new Error('Mineflayer villager.trade is unavailable');
  }
  await villagerWindow.trade(0, count);
}

async function creativeGiveDiamond(bot) {
  if (!bot.creative || typeof bot.creative.setInventorySlot !== 'function') {
    throw new Error('Mineflayer creative inventory API is unavailable');
  }
  const Item = require('prismarine-item')(bot.registry);
  const diamond = new Item(bot.registry.itemsByName.diamond.id, 1);
  await bot.creative.setInventorySlot(36, diamond);
}

function blockAtInfo(bot, x, y, z) {
  const block = bot.blockAt(new Vec3(x, y, z));
  return block ? { name: block.name, type: block.type } : null;
}

function once(emitter, event) {
  return new Promise((resolve) => emitter.once(event, (...args) => resolve(args.length <= 1 ? args[0] : args)));
}

function timeout(ms, message) {
  return new Promise((_, reject) => setTimeout(() => reject(new Error(message)), ms));
}

function stringify(value) {
  if (typeof value === 'string') {
    return value;
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

module.exports = {
  connectBot,
  disconnectBot,
  countItem,
  openBlock,
  blockAtInfo,
  shiftClickSlot,
  normalWithdrawSlot,
  firstInventorySlot,
  firstEmptyInventorySlot,
  swapInventorySlots,
  craftDiamonds,
  creativeGiveDiamond,
  numberKeySwap,
  doubleClickSlot,
  dragFromCursorToSlots,
  openVillagerByUuid,
  tradeFirstOffer
};
