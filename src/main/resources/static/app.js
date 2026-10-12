const API_URL = "/api/assets";

let assets = [];

document.addEventListener("DOMContentLoaded", () => {
    loadAssets();

    document
        .getElementById("assetForm")
        .addEventListener("submit", saveAsset);
});


async function loadAssets() {

    try {

        const response = await fetch(API_URL);

        if (!response.ok) {
            throw new Error("Failed to load assets");
        }

        assets = await response.json();

        renderAssets();
        updateStatistics();

    } catch (error) {

        console.error(error);

        showToast("Unable to connect to asset API");

    }
}


function renderAssets(list = assets) {

    const tbody = document.getElementById("assetTableBody");
    const emptyState = document.getElementById("emptyState");

    tbody.innerHTML = "";

    if (list.length === 0) {

        emptyState.style.display = "block";

        return;

    }

    emptyState.style.display = "none";

    list.forEach(asset => {

        const row = document.createElement("tr");

        const statusClass =
            asset.status
                ? asset.status.toLowerCase().replace(/\s+/g, "-")
                : "";

        row.innerHTML = `
            <td>
                <span class="asset-code">
                    ${escapeHtml(asset.assetCode)}
                </span>
            </td>

            <td>
                <span class="asset-name">
                    ${escapeHtml(asset.name)}
                </span>
            </td>

            <td>
                ${escapeHtml(asset.assetType)}
            </td>

            <td>
                ${escapeHtml(asset.brand || "-")}
                ${asset.model ? " / " + escapeHtml(asset.model) : ""}
            </td>

            <td>
                ${escapeHtml(asset.serialNumber || "-")}
            </td>

            <td>
                <span class="badge ${statusClass}">
                    ${escapeHtml(asset.status)}
                </span>
            </td>

            <td>
                ${escapeHtml(asset.location)}
            </td>

            <td>
                <button
                    class="action-btn edit-btn"
                    onclick="editAsset(${asset.id})"
                    title="Edit">
                    ✎
                </button>

                <button
                    class="action-btn delete-btn"
                    onclick="deleteAsset(${asset.id})"
                    title="Delete">
                    🗑
                </button>
            </td>
        `;

        tbody.appendChild(row);

    });
}


function updateStatistics() {

    document.getElementById("totalAssets").textContent =
        assets.length;

    document.getElementById("availableAssets").textContent =
        assets.filter(a => a.status === "Available").length;

    document.getElementById("allocatedAssets").textContent =
        assets.filter(a => a.status === "Allocated").length;

    document.getElementById("maintenanceAssets").textContent =
        assets.filter(a => a.status === "Maintenance").length;
}


function filterAssets() {

    const query =
        document
            .getElementById("searchInput")
            .value
            .toLowerCase()
            .trim();

    const filtered = assets.filter(asset => {

        return (
            String(asset.assetCode || "").toLowerCase().includes(query) ||
            String(asset.name || "").toLowerCase().includes(query) ||
            String(asset.assetType || "").toLowerCase().includes(query) ||
            String(asset.brand || "").toLowerCase().includes(query) ||
            String(asset.model || "").toLowerCase().includes(query) ||
            String(asset.serialNumber || "").toLowerCase().includes(query) ||
            String(asset.status || "").toLowerCase().includes(query) ||
            String(asset.location || "").toLowerCase().includes(query)
        );

    });

    renderAssets(filtered);
}


function openAddForm() {

    document.getElementById("modalTitle").textContent =
        "Add Asset";

    document.getElementById("assetForm").reset();

    document.getElementById("assetId").value = "";

    document
        .getElementById("assetModal")
        .classList.add("show");
}


function closeForm() {

    document
        .getElementById("assetModal")
        .classList.remove("show");
}


function editAsset(id) {

    const asset = assets.find(a => a.id === id);

    if (!asset) {
        return;
    }

    document.getElementById("modalTitle").textContent =
        "Edit Asset";

    document.getElementById("assetId").value =
        asset.id;

    document.getElementById("assetCode").value =
        asset.assetCode || "";

    document.getElementById("assetType").value =
        asset.assetType || "";

    document.getElementById("name").value =
        asset.name || "";

    document.getElementById("brand").value =
        asset.brand || "";

    document.getElementById("model").value =
        asset.model || "";

    document.getElementById("serialNumber").value =
        asset.serialNumber || "";

    document.getElementById("status").value =
        asset.status || "";

    document.getElementById("location").value =
        asset.location || "";

    document
        .getElementById("assetModal")
        .classList.add("show");
}


async function saveAsset(event) {

    event.preventDefault();

    const id =
        document.getElementById("assetId").value;

    const asset = {

        assetCode:
            document.getElementById("assetCode").value.trim(),

        assetType:
            document.getElementById("assetType").value,

        name:
            document.getElementById("name").value.trim(),

        brand:
            document.getElementById("brand").value.trim(),

        model:
            document.getElementById("model").value.trim(),

        serialNumber:
            document.getElementById("serialNumber").value.trim(),

        status:
            document.getElementById("status").value,

        location:
            document.getElementById("location").value.trim()

    };

    try {

        const response = await fetch(
            id ? `${API_URL}/${id}` : API_URL,
            {
                method: id ? "PUT" : "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(asset)
            }
        );

        if (!response.ok) {

            const message = await response.text();

            throw new Error(message || "Request failed");

        }

        closeForm();

        await loadAssets();

        showToast(
            id
                ? "Asset updated successfully"
                : "Asset created successfully"
        );

    } catch (error) {

        console.error(error);

        showToast("Failed to save asset");

    }
}


async function deleteAsset(id) {

    const asset = assets.find(a => a.id === id);

    if (!asset) {
        return;
    }

    const confirmed =
        confirm(
            `Delete asset "${asset.name}" (${asset.assetCode})?`
        );

    if (!confirmed) {
        return;
    }

    try {

        const response =
            await fetch(`${API_URL}/${id}`, {
                method: "DELETE"
            });

        if (!response.ok) {
            throw new Error("Delete failed");
        }

        await loadAssets();

        showToast("Asset deleted successfully");

    } catch (error) {

        console.error(error);

        showToast("Failed to delete asset");

    }
}


function showToast(message) {

    const toast =
        document.getElementById("toast");

    toast.textContent = message;

    toast.classList.add("show");

    setTimeout(() => {

        toast.classList.remove("show");

    }, 3000);
}


function escapeHtml(value) {

    const div = document.createElement("div");

    div.textContent = value;

    return div.innerHTML;
}