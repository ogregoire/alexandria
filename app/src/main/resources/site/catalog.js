// Client-side search over search-index.json. Every query token must appear somewhere in
// an entry's text blob, which already contains titles, authors, translators, publishers,
// series, subjects, shelves and reading states — so "grossman penguin" narrows correctly.
(function () {
  var box = document.getElementById('q');
  if (!box) {
    return;
  }
  var list = document.getElementById('entries');
  var count = document.getElementById('count');
  var empty = document.getElementById('empty');
  var entries = Array.prototype.slice.call(list.querySelectorAll('.entry'));
  var index = {};
  var total = entries.length;

  function fold(text) {
    return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  }

  function report(shown) {
    count.textContent = shown === total
      ? total + (total === 1 ? ' work' : ' works')
      : shown + ' of ' + total;
    empty.hidden = shown !== 0;
  }

  function filter() {
    var tokens = fold(box.value).split(/\s+/).filter(Boolean);
    var shown = 0;
    entries.forEach(function (entry) {
      var blob = index[entry.dataset.id] || '';
      var match = tokens.every(function (token) {
        return blob.indexOf(token) !== -1;
      });
      entry.hidden = !match;
      if (match) {
        shown++;
      }
    });
    report(shown);
  }

  fetch('search-index.json')
    .then(function (response) { return response.json(); })
    .then(function (data) {
      data.forEach(function (entry) {
        index[entry.id] = fold(entry.text + ' ' + entry.title + ' ' + entry.author);
      });
      box.addEventListener('input', filter);
      filter();
    })
    .catch(function () {
      // Opened straight from the filesystem, where fetch is blocked: keep the full list.
      count.textContent = total + (total === 1 ? ' work' : ' works');
      box.hidden = true;
    });
})();
