package be.imgn.alexandria.infrastructure.web;

import java.util.List;
import java.util.Map;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.shared.Note;
import be.imgn.alexandria.infrastructure.Template;

/** Browsing and editing the Item aggregate — the copy you actually own. */
final class ItemPages {

    private final CatalogService service;

    ItemPages(CatalogService service) {
        this.service = service;
    }

    String list() {
        List<List<String>> rows = service.catalog().items().stream()
                .map(item -> List.of(
                        Html.link("/items/" + item.id().value(), item.id().value()),
                        service.catalog()
                                .manifestation(item.embodiedIn())
                                .map(m -> Html.link(
                                        "/manifestations/" + m.id().value(),
                                        m.title().main()))
                                .orElse("<span class=\"broken\">missing edition</span>"),
                        Html.escape(item.location().display()),
                        Html.escape(item.reading().display()),
                        Html.escape(item.condition().label())))
                .toList();
        return Html.page("Items", Html.link("/", "Home") + " / Items", """
                <h1>Items</h1>
                <p><a class="button" href="/items/new">New item</a></p>
                %s
                """.formatted(
                        Html.table(List.of("Copy", "Edition", "Where", "Reading", "Condition"), rows)));
    }

    /** A blank form for a record that does not exist yet. */
    String edit() {
        return edit((Item) null);
    }

    String edit(Item item) {
        String heading = item == null ? "New item" : item.id().value();
        String id = item == null ? "" : item.id().value();
        Map<String, String> editions = service.manifestationChoices();

        if (editions.isEmpty()) {
            return Html.page(
                    heading,
                    Html.link("/items", "Items"),
                    "<h1>New item</h1><p class=\"hint\">Create a manifestation first — "
                            + "an item is always a copy of one.</p>");
        }

        String deleteButton = item == null ? "" : """
                <form method="post" action="/items/%s/delete" class="danger"
                      onsubmit="return confirm('Delete this copy?')">
                  <button type="submit">Delete item</button>
                </form>
                """.formatted(Html.escape(id));

        return Html.page(
                heading,
                Html.link("/items", "Items") + " / " + Html.escape(heading),
                Template.of("""
                <h1>{heading}</h1>
                <form method="post" action="/items/{action}">
                  <fieldset><legend>Copy</legend>
                    {id}
                    {edition}
                    {condition}
                    {notes}
                  </fieldset>
                  <fieldset><legend>Provenance</legend>{acquisition}</fieldset>
                  <fieldset><legend>Whereabouts</legend>{location}</fieldset>
                  <fieldset><legend>Reading</legend>{reading}</fieldset>
                  <button type="submit">Save</button>
                </form>
                {delete}
                """)
                        .with("heading", heading)
                        .with("action", id.isEmpty() ? "new" : id)
                        .withMarkup(
                                "id",
                                item == null
                                        ? Html.textField("id", "Identifier (slug)", "")
                                        : WorkPages.readOnly("Identifier", id) + WorkPages.hidden("id", id))
                        .withMarkup(
                                "edition",
                                Html.select(
                                        "manifestation",
                                        "Edition",
                                        editions,
                                        item == null
                                                ? editions.keySet().iterator().next()
                                                : item.embodiedIn().value()))
                        .withMarkup(
                                "condition",
                                Html.select(
                                        "condition",
                                        "Condition",
                                        VariantForms.conditions(),
                                        item == null
                                                ? Condition.UNGRADED.name()
                                                : item.condition().name()))
                        .withMarkup(
                                "notes",
                                Html.textArea(
                                        "notes",
                                        "Notes",
                                        item == null ? "" : item.notes().text()))
                        .withMarkup(
                                "acquisition",
                                VariantForms.acquisition(
                                        "acquisition", "Acquired", item == null ? null : item.acquisition()))
                        .withMarkup(
                                "location",
                                VariantForms.location("location", "Location", item == null ? null : item.location()))
                        .withMarkup(
                                "reading",
                                VariantForms.reading("reading", "Progress", item == null ? null : item.reading()))
                        .withMarkup("delete", deleteButton)
                        .render());
    }

    Item read(FormData form) {
        return new Item(
                ItemId.of(form.required("id")),
                ManifestationId.of(form.required("manifestation")),
                VariantForms.readAcquisition(form, "acquisition"),
                VariantForms.readLocation(form, "location"),
                VariantForms.readReading(form, "reading"),
                Condition.valueOf(form.required("condition")),
                Note.of(form.orEmpty("notes")));
    }
}
