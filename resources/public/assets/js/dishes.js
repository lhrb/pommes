function formatEuro(value) {
  return `€${value.toFixed(2).replace(".", ",")}`;
}

function dishCost(dish) {
  return Array.from(dish.querySelectorAll("select option:checked"))
    .reduce((total, option) => total + Number(option.dataset.cost || 0), 0);
}

function updateDishCost(event) {
  if (event.target.tagName !== "SELECT") return;

  const dish = event.target.closest("[data-dish]");
  if (!dish) return;

  dish.querySelector("[data-cost-total]").textContent = formatEuro(dishCost(dish));
}

document.addEventListener("change", updateDishCost);

document.addEventListener("htmx:load", ({ detail: { elt } }) => {
  window.Alpine?.initTree(elt);
});
