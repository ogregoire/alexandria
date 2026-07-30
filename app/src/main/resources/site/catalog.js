// Client-side search over search-index.json.
//
// The page ships the whole catalogue in alphabetical order and this script hides it until
// something is typed — so with no JavaScript at all the catalogue is simply all there,
// and with it the page opens as a search field and nothing else.
//
// Every query token must appear somewhere in an entry's text blob, which holds titles,
// names, aliases, roles, publishers, series, subjects, shelves and reading states — so
// "grossman penguin" narrows. An entry whose *heading* matches sorts above one that only
// matches deeper in: searching "lauzon" wants the translator first and the book he
// translated second.

// Claim the page before it paints. This file is loaded from <head> without defer, so this
// line runs before the list exists: the stylesheet hides the list for a document marked
// "js", and the marker is set early enough that the full catalogue is never drawn and then
// snatched away. If this file fails to load, the class is never added and the catalogue
// renders in full — which is the same thing that happens with scripting turned off.
document.documentElement.classList.add('js');

document.addEventListener('DOMContentLoaded', function () {
  var box = document.getElementById('q');
  var list = document.getElementById('entries');
  if (!box || !list) {
    return;
  }
  var count = document.getElementById('count');
  var empty = document.getElementById('empty');
  var rows = Array.prototype.slice.call(list.querySelectorAll('[data-id]'));
  var index = {};

  function fold(text) {
    return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  }

  function show(row, rank) {
    row.hidden = false;
    // Rank rides on flex order, so the two groups reorder without touching the DOM and
    // each stays in the alphabetical order the page was written in.
    row.style.order = rank;
  }

  function hide(row) {
    row.hidden = true;
    row.style.order = '';
  }

  function filter() {
    var query = box.value.trim();
    var tokens = fold(query).split(/\s+/).filter(Boolean);

    if (tokens.length === 0) {
      list.classList.remove('is-searching');
      count.textContent = '';
      empty.hidden = true;
      return;
    }
    list.classList.add('is-searching');

    var shown = 0;
    rows.forEach(function (row) {
      var entry = index[row.dataset.id];
      if (!entry) {
        hide(row);
        return;
      }
      var everywhere = tokens.every(function (token) {
        return entry.text.indexOf(token) !== -1;
      });
      if (!everywhere) {
        hide(row);
        return;
      }
      var inHeading = tokens.every(function (token) {
        return entry.title.indexOf(token) !== -1;
      });
      show(row, inHeading ? 0 : 1);
      shown++;
    });

    count.textContent = shown + (shown === 1 ? ' result' : ' results');
    empty.hidden = shown !== 0;
  }

  fetch('search-index.json')
    .then(function (response) { return response.json(); })
    .then(function (data) {
      data.forEach(function (entry) {
        index[entry.id] = { title: fold(entry.title), text: fold(entry.text + ' ' + entry.title) };
      });
      box.addEventListener('input', filter);
      filter();
    })
    .catch(function () {
      // Opened straight from the filesystem, where fetch is blocked. Drop the marker so the
      // stylesheet shows the catalogue in full again, and take away a field that would do
      // nothing.
      document.documentElement.classList.remove('js');
      box.closest('.find').hidden = true;
    });
});
