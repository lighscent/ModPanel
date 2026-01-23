// Profile Scripts Module
let currentPlayerName = "";
let currentPlayerUUID = "";
let selectedSlot = null;

// Utility Functions
function getQueryParams() {
    const params = {};
    const queryString = window.location.search.substring(1);
    const pairs = queryString.split("&");

    for (let pair of pairs) {
        const [key, value] = pair.split("=");
        params[decodeURIComponent(key)] = decodeURIComponent(value);
    }

    return params;
}

// Load HTML modules
async function loadModule(containerId, modulePath) {
    try {
        const response = await fetch(modulePath);
        const html = await response.text();
        document.getElementById(containerId).innerHTML = html;
    } catch (error) {
        console.error(`Failed to load module ${modulePath}:`, error);
    }
}

// Initialize modules
async function initializeModules() {
    await loadModule('header-container', '/profile/header.html');
    await loadModule('footer-container', '/profile/footer.html');
}

// Player Inventory Functions
async function loadPlayerInventory() {
    const params = getQueryParams();
    currentPlayerUUID = params.uuid || "";

    if (!currentPlayerUUID) {
        document.getElementById("inventory-content").innerHTML =
            '<div class="error">No UUID specified</div>';
        return;
    }

    try {
        const response = await fetch(
            `/api/inventory?uuid=${encodeURIComponent(currentPlayerUUID)}`,
        );
        const data = await response.json();

        // Update player info
        currentPlayerName = data.player.name;
        document.getElementById("player-name").textContent = currentPlayerName;
        document.getElementById("player-status").textContent = data.player
            .online
            ? "Online"
            : "Offline";
        document.getElementById("player-status").className = `player-status ${data.player.online ? "online" : "offline"
            }`;

        // Update player head
        const headDiv = document.getElementById("player-head");
        headDiv.innerHTML = "";
        const headImg = document.createElement("img");
        headImg.src = `https://cravatar.eu/avatar/${currentPlayerUUID}/48.png`;
        headImg.alt = `${data.player.name}'s head`;
        headImg.style.width = "48px";
        headImg.style.height = "48px";
        headImg.style.borderRadius = "5px";
        headImg.onerror = function () {
            headDiv.innerHTML = '<i class="fas fa-user"></i>';
        };
        headDiv.appendChild(headImg);

        // Render inventory
        renderInventory(data.inventory);
    } catch (error) {
        console.error("Failed to load inventory:", error);
        document.getElementById("inventory-content").innerHTML =
            '<div class="error">Failed to load profile</div>';
    }
}

function renderInventory(inventory) {
    let html = '<div class="inventory-container">';

    // Create main inventory grid (4 rows x 9 columns)
    html += '<div class="inventory-section">';

    // Off-hand slot (left side)
    html += '<div class="inventory-section-wrapper">';
    html += '<div class="section-title">Left Hand</div>';
    html += '<div class="inventory-offhand">';
    if (inventory.offhand && inventory.offhand.length > 0) {
        html += renderInventorySlot(inventory.offhand[0], 40);
    } else {
        html += renderInventorySlot(null, 40);
    }
    html += "</div></div>";

    // Main inventory + armor
    html += '<div class="inventory-section-wrapper">';
    html += '<div class="section-title">Inventory</div>';
    html += '<div class="inventory-main">';

    // Row 1-3: Main inventory (slots 9-35, but displayed as rows 1-3)
    for (let row = 0; row < 3; row++) {
        for (let col = 0; col < 9; col++) {
            const slotIndex = 9 + row * 9 + col; // 9-17, 18-26, 27-35
            const item =
                inventory.main && inventory.main[slotIndex]
                    ? inventory.main[slotIndex]
                    : null;
            html += renderInventorySlot(item, slotIndex);
        }
    }

    // Row 4: Hotbar (slots 0-8)
    for (let col = 0; col < 9; col++) {
        const slotIndex = col; // 0-8
        const item =
            inventory.main && inventory.main[slotIndex]
                ? inventory.main[slotIndex]
                : null;
        html += renderInventorySlot(item, slotIndex);
    }

    html += "</div></div>";

    // Armor slots (right side)
    html += '<div class="inventory-section-wrapper">';
    html += '<div class="section-title">Armors</div>';
    html += '<div class="inventory-armor">';
    if (inventory.armor && inventory.armor.length > 0) {
        // Armor order: helmet (39), chestplate (38), leggings (37), boots (36)
        const armorOrder = [3, 2, 1, 0]; // helmet, chestplate, leggings, boots
        for (let i of armorOrder) {
            const slotIndex = 36 + i;
            html += renderInventorySlot(inventory.armor[i], slotIndex);
        }
    } else {
        // Render empty armor slots
        for (let i = 39; i >= 36; i--) {
            html += renderInventorySlot(null, i);
        }
    }
    html += "</div></div>";

    html += "</div></div>";

    document.getElementById("inventory-content").innerHTML = html;

    // Add drag and drop functionality
    setupDragAndDrop();
}

function setupDragAndDrop() {
    const slots = document.querySelectorAll(".inventory-slot");
    let draggedSlot = null;
    let draggedItem = null;

    // Track mouse position for tooltip
    slots.forEach((slot) => {
        slot.addEventListener("mousemove", (e) => {
            const tooltip = slot.querySelector(".item-tooltip");
            if (tooltip) {
                tooltip.style.setProperty("--tooltip-x", `${e.clientX}px`);
                tooltip.style.setProperty("--tooltip-y", `${e.clientY}px`);
            }
        });
    });

    slots.forEach((slot) => {
        // Drag start
        slot.addEventListener("dragstart", (e) => {
            draggedSlot = slot;
            draggedItem = slot.querySelector(".item-icon");
            if (draggedItem) {
                slot.classList.add("dragging");
                // Masquer tous les tooltips pendant le drag
                document.querySelectorAll(".item-tooltip").forEach((tooltip) => {
                    tooltip.style.display = "none";
                });
                e.dataTransfer.effectAllowed = "move";
                e.dataTransfer.setData("text/html", slot.innerHTML);
            } else {
                e.preventDefault();
            }
        });

        // Drag end
        slot.addEventListener("dragend", (e) => {
            slots.forEach((s) =>
                s.classList.remove("dragging", "drag-over", "drag-invalid"),
            );
            draggedSlot = null;
            draggedItem = null;
            // Réactiver les tooltips après le drag
            document.querySelectorAll(".item-tooltip").forEach((tooltip) => {
                tooltip.style.display = "";
            });
        });

        // Drag over
        slot.addEventListener("dragover", (e) => {
            e.preventDefault();
            if (draggedSlot && draggedSlot !== slot) {
                // Get item type for validation
                let itemType = null;
                if (draggedItem && draggedItem.src) {
                    const urlParts = draggedItem.src.split("/");
                    const itemName = urlParts[urlParts.length - 1].replace(
                        ".png",
                        "",
                    );
                    itemType = "minecraft:" + itemName;
                }

                const fromSlot = parseInt(draggedSlot.dataset.slot);
                const toSlot = parseInt(slot.dataset.slot);

                if (isValidArmorMove(fromSlot, toSlot, itemType)) {
                    e.dataTransfer.dropEffect = "move";
                    slot.classList.add("drag-over");
                    slot.classList.remove("drag-invalid");
                } else {
                    e.dataTransfer.dropEffect = "none";
                    slot.classList.add("drag-invalid");
                    slot.classList.remove("drag-over");
                }
            }
        });

        // Drag leave
        slot.addEventListener("dragleave", (e) => {
            slot.classList.remove("drag-over", "drag-invalid");
        });

        // Drop
        slot.addEventListener("drop", (e) => {
            e.preventDefault();
            slot.classList.remove("drag-over");

            if (draggedSlot && draggedSlot !== slot && draggedItem) {
                const fromSlot = parseInt(draggedSlot.dataset.slot);
                const toSlot = parseInt(slot.dataset.slot);

                // Get item type for validation
                let itemType = null;
                if (draggedItem && draggedItem.src) {
                    const urlParts = draggedItem.src.split("/");
                    const itemName = urlParts[urlParts.length - 1].replace(
                        ".png",
                        "",
                    );
                    itemType = "minecraft:" + itemName;
                }

                // Validate the move
                if (isValidArmorMove(fromSlot, toSlot, itemType)) {
                    // Perform the move
                    moveItem(fromSlot, toSlot);
                } else {
                    alert(
                        "Invalid move: Armor items can only be placed in their corresponding armor slots.",
                    );
                }
            }
        });

        // Click to move (alternative to drag/drop)
        slot.addEventListener("click", (e) => {
            if (selectedSlot) {
                if (selectedSlot === slot) {
                    // Deselect
                    selectedSlot.classList.remove("selected");
                    selectedSlot = null;
                } else {
                    // Move item
                    const fromSlot = parseInt(selectedSlot.dataset.slot);
                    const toSlot = parseInt(slot.dataset.slot);
                    moveItem(fromSlot, toSlot);
                    selectedSlot.classList.remove("selected");
                    selectedSlot = null;
                }
            } else if (slot.querySelector(".item-icon")) {
                // Select slot
                slot.classList.add("selected");
                selectedSlot = slot;
            }
        });
    });
}

function isValidArmorMove(fromSlot, toSlot, itemType) {
    // Allow moving from armor slots to any inventory slot
    if (fromSlot >= 36 && fromSlot <= 39 && toSlot >= 0 && toSlot <= 35) {
        return true;
    }

    // Validate moving to armor slots - only allow appropriate armor types
    if (toSlot >= 36 && toSlot <= 39 && fromSlot >= 0 && fromSlot <= 35) {
        if (!itemType) return false;

        const armorType = itemType.replace("minecraft:", "");

        switch (toSlot) {
            case 39: // Helmet slot
                return armorType.includes("_helmet");
            case 38: // Chestplate slot
                return armorType.includes("_chestplate");
            case 37: // Leggings slot
                return armorType.includes("_leggings");
            case 36: // Boots slot
                return armorType.includes("_boots");
        }
    }

    // Allow all other moves (inventory to inventory)
    return true;
}

async function moveItem(fromSlot, toSlot) {
    // Get the item being moved to validate armor restrictions
    const fromSlotElement = document.querySelector(`[data-slot="${fromSlot}"]`);
    const toSlotElement = document.querySelector(`[data-slot="${toSlot}"]`);

    if (!fromSlotElement || !toSlotElement) return;

    const itemIcon = fromSlotElement.querySelector(".item-icon");
    let itemType = null;

    if (itemIcon && itemIcon.src) {
        // Extract item type from the image URL
        const urlParts = itemIcon.src.split("/");
        const itemName = urlParts[urlParts.length - 1].replace(".png", "");
        itemType = "minecraft:" + itemName;
    }

    // Validate the move
    if (!isValidArmorMove(fromSlot, toSlot, itemType)) {
        alert("Invalid move: Armor items can only be placed in their corresponding armor slots.");
        return;
    }

    // --- Optimistic UI Update ---
    // Save current state in case we need to revert
    const fromHTML = fromSlotElement.innerHTML;
    const fromDraggable = fromSlotElement.getAttribute("draggable");
    const fromEmpty = fromSlotElement.classList.contains("empty");

    const toHTML = toSlotElement.innerHTML;
    const toDraggable = toSlotElement.getAttribute("draggable");
    const toEmpty = toSlotElement.classList.contains("empty");

    // Perform the swap immediately
    fromSlotElement.innerHTML = toHTML;
    fromSlotElement.setAttribute("draggable", toDraggable);
    if (toEmpty) fromSlotElement.classList.add("empty");
    else fromSlotElement.classList.remove("empty");

    toSlotElement.innerHTML = fromHTML;
    toSlotElement.setAttribute("draggable", fromDraggable);
    if (fromEmpty) toSlotElement.classList.add("empty");
    else toSlotElement.classList.remove("empty");

    // Clear any selection/drag classes
    fromSlotElement.classList.remove("selected", "dragging", "drag-over");
    toSlotElement.classList.remove("selected", "dragging", "drag-over");

    try {
        const formData = new URLSearchParams();
        formData.append("uuid", currentPlayerUUID);
        formData.append("fromSlot", fromSlot);
        formData.append("toSlot", toSlot);

        const response = await fetch("/api/move-item", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
            body: formData,
        });

        const result = await response.json();

        if (!result.success) {
            // Revert if server rejected the move
            fromSlotElement.innerHTML = fromHTML;
            fromSlotElement.setAttribute("draggable", fromDraggable);
            if (fromEmpty) fromSlotElement.classList.add("empty");
            else fromSlotElement.classList.remove("empty");

            toSlotElement.innerHTML = toHTML;
            toSlotElement.setAttribute("draggable", toDraggable);
            if (toEmpty) toSlotElement.classList.add("empty");
            else toSlotElement.classList.remove("empty");

            console.error("Failed to move item:", result.error);
            alert("Failed to move item: " + (result.error || "Unknown error"));
        }
        // Success! No need to reload everything, UI is already updated.
    } catch (error) {
        // Revert on network error
        fromSlotElement.innerHTML = fromHTML;
        fromSlotElement.setAttribute("draggable", fromDraggable);
        if (fromEmpty) fromSlotElement.classList.add("empty");
        else fromSlotElement.classList.remove("empty");

        toSlotElement.innerHTML = toHTML;
        toSlotElement.setAttribute("draggable", toDraggable);
        if (toEmpty) toSlotElement.classList.add("empty");
        else toSlotElement.classList.remove("empty");

        console.error("Failed to move item:", error);
        alert("Failed to move item due to a connection error.");
    }
}

function renderInventorySlot(item, slotIndex) {
    if (!item || !item.type) {
        return `<div class="inventory-slot empty" data-slot="${slotIndex}" draggable="false"></div>`;
    }

    const itemName =
        item.displayName ||
        item.type.replace("minecraft:", "").replace(/_/g, " ");
    let tooltip = itemName;
    if (item.count > 1) {
        tooltip += ` (${item.count})`;
    }

    // Add enchantments to tooltip
    if (item.enchantments && item.enchantments.length > 0) {
        tooltip += "\n\nEnchantments:";
        item.enchantments.forEach((enchant) => {
            const enchantName = enchant.name
                .replace("minecraft:", "")
                .replace(/_/g, " ");
            tooltip += `\n<span class="enchantment">${enchantName} ${enchant.level}</span>`;
        });
    }

    let enchantmentIndicator = "";
    if (item.enchantments && item.enchantments.length > 0) {
        enchantmentIndicator = '<div class="item-enchantment">✨</div>';
    }

    return `
    <div class="inventory-slot" data-slot="${slotIndex}" draggable="true">
      <img src="/assets/minecraft/${item.type.replace(
        "minecraft:",
        "",
    ).toLowerCase()}.png"
           alt="${itemName}"
           class="item-icon"
           onerror="this.style.display='none'">
      ${item.count > 1
            ? `<span class="item-count">${item.count}</span>`
            : ""
        }
      ${enchantmentIndicator}
      <div class="item-tooltip">${tooltip}</div>
    </div>
  `;
}

async function checkVersion() {
    const versionContainer = document.getElementById("version-status");
    const versionInfo = document.getElementById("version-info");

    try {
        const response = await fetch("/api/version");
        const data = await response.json();

        if (data.updateAvailable) {
            versionContainer.style.display = "inline-flex";
            versionInfo.innerHTML = `
          <a href="https://modrinth.com/plugin/modpanel" target="_blank" class="footer-link version-update">
            <i class="fas fa-exclamation-circle"></i>Update Available (${data.latest})
          </a>
      `;
        } else {
            versionContainer.style.display = "inline-flex";
            versionInfo.innerHTML = `
          <span class="footer-link version-latest">
            <i class="fas fa-check-circle"></i>${data.current}
          </span>
      `;
        }
    } catch (error) {
        console.error("Failed to check version:", error);
        versionContainer.style.display = "none";
    }
}

// Initialize application
window.addEventListener("load", async () => {
    await initializeModules();
    loadPlayerInventory();
    checkVersion();
});

// Event listener for live updates
const eventSource = new EventSource("/api/events");
eventSource.onmessage = function (event) {
    if (event.data === "refresh") {
        loadPlayerInventory();
    }
};