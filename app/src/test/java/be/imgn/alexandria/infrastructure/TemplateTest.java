package be.imgn.alexandria.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class TemplateTest {

    @Test
    void fillsSlotsByName() {
        String page = Template.of("<h1>{title}</h1><p>{byline}</p>")
                .with("title", "Don Quixote")
                .with("byline", "Miguel de Cervantes")
                .render();

        assertThat(page).isEqualTo("<h1>Don Quixote</h1><p>Miguel de Cervantes</p>");
    }

    @Test
    void escapesWhatItInserts() {
        String page = Template.of("<p>{note}</p>")
                .with("note", "<script>alert(1)</script> & \"quoted\"")
                .render();

        assertThat(page)
                .doesNotContain("<script>")
                .isEqualTo("<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quoted&quot;</p>");
    }

    @Test
    void leavesMarkupAloneWhenAskedTo() {
        String page = Template.of("<div>{body}</div>")
                .withMarkup("body", "<a href=\"/works/x\">A title</a>")
                .render();

        assertThat(page).isEqualTo("<div><a href=\"/works/x\">A title</a></div>");
    }

    @Test
    void fillsTheSameSlotEverywhereItAppears() {
        String page = Template.of("<a href=\"{root}/a.css\">x</a><script src=\"{root}/b.js\"></script>")
                .with("root", "..")
                .render();

        assertThat(page).isEqualTo("<a href=\"../a.css\">x</a><script src=\"../b.js\"></script>");
    }

    /** The failure mode this class exists to prevent: a slot nobody filled, silently rendering blank. */
    @Test
    void refusesToRenderWithASlotLeftEmpty() {
        assertThatThrownBy(() -> Template.of("<h1>{title}</h1><p>{byline}</p>")
                        .with("title", "Don Quixote")
                        .render())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("byline");
    }

    /** And the other direction: a value whose slot was renamed or never existed. */
    @Test
    void refusesAValueThatMatchesNoSlot() {
        assertThatThrownBy(() -> Template.of("<h1>{title}</h1>").with("titel", "typo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titel")
                .hasMessageContaining("title");
    }

    @Test
    void leavesBracesThatAreNotSlotsAlone() {
        String page = Template.of("<style>.a { color: red } </style><p>{note}</p>")
                .with("note", "kept")
                .render();

        assertThat(page).isEqualTo("<style>.a { color: red } </style><p>kept</p>");
    }

    /** A replacement carrying $ or \ must not be read as a regex backreference. */
    @Test
    void treatsTheValueAsTextNotAsAReplacementPattern() {
        String page = Template.of("<p>{price}</p>").with("price", "$5 \\ 10").render();

        assertThat(page).isEqualTo("<p>$5 \\ 10</p>");
    }

    // ------------------------------------------------------------------ blocks

    @Test
    void repeatsABlockOncePerItem() {
        String page = Template.of("<ul>{#each rows}<li>{title}</li>{/each}</ul>")
                .each("rows", List.of("Don Quixote", "The Eye of the World"), (row, title) -> row.with("title", title))
                .render();

        assertThat(page).isEqualTo("<ul><li>Don Quixote</li><li>The Eye of the World</li></ul>");
    }

    @Test
    void rendersNothingForAnEmptyCollection() {
        String page = Template.of("<ul>{#each rows}<li>{title}</li>{/each}</ul>")
                .each("rows", List.<String>of(), (row, title) -> row.with("title", title))
                .render();

        assertThat(page).isEqualTo("<ul></ul>");
    }

    @Test
    void escapesInsideALoopToo() {
        String page = Template.of("{#each rows}<li>{title}</li>{/each}")
                .each("rows", List.of("<b>bold</b>"), (row, title) -> row.with("title", title))
                .render();

        assertThat(page).isEqualTo("<li>&lt;b&gt;bold&lt;/b&gt;</li>");
    }

    @Test
    void keepsOrOmitsABranch() {
        String source = "<p>a</p>{#if held}<p>on the shelf</p>{/if}";

        assertThat(Template.of(source).when("held", true).render()).isEqualTo("<p>a</p><p>on the shelf</p>");
        assertThat(Template.of(source).when("held", false).render()).isEqualTo("<p>a</p>");
    }

    @Test
    void takesTheElseBranchWhenFalse() {
        String source = "{#if held}<p>held</p>{#else}<p>not held</p>{/if}";

        assertThat(Template.of(source).when("held", true).render()).isEqualTo("<p>held</p>");
        assertThat(Template.of(source).when("held", false).render()).isEqualTo("<p>not held</p>");
    }

    /** A slot in the branch not taken is never rendered, so it never needs a value. */
    @Test
    void asksOnlyForWhatItActuallyRenders() {
        String page = Template.of("{#if named}<p>{name}</p>{#else}<p>Anonymous</p>{/if}")
                .when("named", false)
                .render();

        assertThat(page).isEqualTo("<p>Anonymous</p>");
    }

    @Test
    void nestsBlocksInsideEachOther() {
        record Shelf(String name, List<String> books) {}
        String page = Template.of("{#each shelves}<h2>{shelf}</h2><ul>{#each books}<li>{book}</li>{/each}</ul>{/each}")
                .each(
                        "shelves",
                        List.of(new Shelf("desk", List.of("Quixote")), new Shelf("attic", List.of())),
                        (block, shelf) -> block.with("shelf", shelf.name())
                                .each("books", shelf.books(), (row, book) -> row.with("book", book)))
                .render();

        assertThat(page).isEqualTo("<h2>desk</h2><ul><li>Quixote</li></ul><h2>attic</h2><ul></ul>");
    }

    /**
     * A branch that does not hold the loop is one node that did not have it, not the end of the search. Looking inside
     * a branch and then giving up hid every block that came after one.
     */
    @Test
    void findsABlockThatSitsAfterABranchNotHoldingIt() {
        String page = Template.of("{#if flag}<p>x</p>{/if}<ul>{#each rows}<li>{name}</li>{/each}</ul>")
                .when("flag", false)
                .each("rows", List.of("a", "b"), (row, name) -> row.with("name", name))
                .render();

        assertThat(page).isEqualTo("<ul><li>a</li><li>b</li></ul>");
    }

    /** And the same for a branch nested inside another branch. */
    @Test
    void findsABlockNestedInsideABranch() {
        String page = Template.of("{#if outer}{#each rows}<li>{name}</li>{/each}{/if}")
                .when("outer", true)
                .each("rows", List.of("a"), (row, name) -> row.with("name", name))
                .render();

        assertThat(page).isEqualTo("<li>a</li>");
    }

    @Test
    void refusesToRenderALoopNobodyBound() {
        assertThatThrownBy(() -> Template.of("{#each rows}<li>{x}</li>{/each}").render())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rows");
    }

    @Test
    void refusesToRenderABranchWithNoCondition() {
        assertThatThrownBy(() -> Template.of("{#if held}x{/if}").render())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("held");
    }

    @Test
    void refusesToBindABlockThatIsNotThere() {
        assertThatThrownBy(() -> Template.of("<p>{x}</p>").each("rows", List.of(), (row, item) -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows");
        assertThatThrownBy(() -> Template.of("<p>{x}</p>").when("held", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("held");
    }

    @Test
    void refusesATemplateWhoseBlocksDoNotClose() {
        assertThatThrownBy(() -> Template.of("{#each rows}<li>x</li>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never closed");
        assertThatThrownBy(() -> Template.of("<li>x</li>{/each}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never opened");
    }

    @Test
    void writesNumbersWithoutEscaping() {
        assertThat(Template.of("<p>{count} works</p>").with("count", 12).render())
                .isEqualTo("<p>12 works</p>");
    }
}
