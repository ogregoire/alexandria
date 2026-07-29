// Shows only the fieldset belonging to the selected variant of a sum-typed field.
// Hidden fieldsets keep their inputs in the DOM but the server only reads the payload
// of the variant named by `<field>.type`, so stale values in the others are ignored.
document.querySelectorAll('.sum').forEach(function (group) {
  var field = group.dataset.sum;
  var select = group.querySelector('select[name="' + field + '.type"]');
  if (!select) {
    return;
  }
  var panels = group.querySelectorAll('[data-variant-of="' + field + '"]');

  function show() {
    panels.forEach(function (panel) {
      panel.hidden = panel.dataset.variant !== select.value;
    });
  }

  select.addEventListener('change', show);
  show();
});
