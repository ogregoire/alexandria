// Two jobs: show only the fieldset belonging to the selected variant of a sum-typed field,
// and check fields as they are filled in.
//
// The client-side checks are a courtesy, never the authority. The server parses the form
// through the same domain constructors either way and hands the whole form back with its
// problems attached, so a browser with JavaScript off loses immediacy and nothing else.

(function () {

  // ---------------------------------------------------------------- sum types
  //
  // Hidden fieldsets keep their inputs in the DOM, but the server only reads the payload of
  // the variant named by `<field>.type`, so stale values in the others are ignored.

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

  // ------------------------------------------------------------ live checking

  function isbnValid(raw) {
    var digits = raw.replace(/[\s-]/g, '').toUpperCase();
    var sum = 0;
    var i;
    if (/^\d{13}$/.test(digits)) {
      for (i = 0; i < 13; i++) {
        sum += parseInt(digits[i], 10) * (i % 2 === 0 ? 1 : 3);
      }
      return sum % 10 === 0;
    }
    if (/^\d{9}[\dX]$/.test(digits)) {
      for (i = 0; i < 9; i++) {
        sum += parseInt(digits[i], 10) * (10 - i);
      }
      sum += digits[9] === 'X' ? 10 : parseInt(digits[9], 10);
      return sum % 11 === 0;
    }
    return false;
  }

  // These mirror the rules the domain enforces, so the message shown now is the message the
  // server would have given.
  var CHECKS = {
    required: function (value) {
      return value.trim() ? null : 'Needed.';
    },
    slug: function (value) {
      if (!value.trim()) {
        return null;
      }
      return /^[a-z0-9]+(-[a-z0-9]+)*$/.test(value.trim())
        ? null
        : 'Lowercase letters, digits and single dashes only.';
    },
    isbn: function (value) {
      if (!value.trim()) {
        return null;
      }
      return isbnValid(value) ? null : 'Check digit does not match — a digit is probably wrong.';
    },
    language: function (value) {
      if (!value.trim()) {
        return null;
      }
      return /^[a-z]{2,3}$/i.test(value.trim())
        ? null
        : 'A two- or three-letter code, like en or fre.';
    },
    money: function (value) {
      if (!value.trim()) {
        return null;
      }
      return /^\d+([.,]\d{1,2})?\s+[A-Za-z]{3}$/.test(value.trim())
        ? null
        : 'Amount then currency, like 28.50 EUR.';
    }
  };

  function problemFor(input) {
    var rules = (input.dataset.check || '').split(/\s+/).filter(Boolean);
    for (var i = 0; i < rules.length; i++) {
      var check = CHECKS[rules[i]];
      if (check) {
        var problem = check(input.value);
        if (problem) {
          return problem;
        }
      }
    }
    return null;
  }

  function render(input, problem) {
    var label = input.closest('label');
    if (!label) {
      return;
    }
    var existing = label.querySelector('.field-error');
    if (problem) {
      label.classList.add('bad');
      input.setAttribute('aria-invalid', 'true');
      if (existing) {
        existing.textContent = problem;
      } else {
        var note = document.createElement('strong');
        note.className = 'field-error';
        note.textContent = problem;
        label.appendChild(note);
      }
    } else {
      label.classList.remove('bad');
      input.removeAttribute('aria-invalid');
      if (existing) {
        existing.remove();
      }
    }
  }

  document.querySelectorAll('[data-check]').forEach(function (input) {
    // Verdict on blur, then on every keystroke once the field has been visited: the message
    // clears the moment it is fixed rather than nagging while it is still being typed.
    var visited = false;
    input.addEventListener('blur', function () {
      visited = true;
      render(input, problemFor(input));
    });
    input.addEventListener('input', function () {
      if (visited) {
        render(input, problemFor(input));
      }
    });
  });

  // A field inside a hidden variant panel does not apply, so it can never block a submit.
  function applies(input) {
    var panel = input.closest('.variant');
    return !panel || !panel.hidden;
  }

  document.querySelectorAll('form').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      var firstBad = null;
      Array.prototype.forEach.call(form.querySelectorAll('[data-check]'), function (input) {
        if (!applies(input)) {
          render(input, null);
          return;
        }
        var problem = problemFor(input);
        render(input, problem);
        if (problem && !firstBad) {
          firstBad = input;
        }
      });
      if (firstBad) {
        event.preventDefault();
        firstBad.focus();
        firstBad.scrollIntoView({block: 'center', behavior: 'smooth'});
      }
    });
  });

  // The summary at the top of a rejected form links to the field that needs attention,
  // opening its variant panel first if the problem is hiding inside a closed one.
  document.querySelectorAll('[data-focus]').forEach(function (link) {
    link.addEventListener('click', function (event) {
      event.preventDefault();
      var target = document.querySelector('[name="' + link.dataset.focus + '"]');
      if (!target) {
        return;
      }
      var panel = target.closest('.variant');
      if (panel && panel.hidden) {
        var group = panel.closest('.sum');
        var select = group && group.querySelector('select[name$=".type"]');
        if (select) {
          select.value = panel.dataset.variant;
          select.dispatchEvent(new Event('change'));
        }
      }
      target.focus();
      target.scrollIntoView({block: 'center', behavior: 'smooth'});
    });
  });
})();
