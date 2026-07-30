package be.imgn.alexandria.infrastructure.json;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentKind;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.Carrier;
import be.imgn.alexandria.domain.manifestation.Extent;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionKind;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkForm;

/**
 * Jackson lives here and nowhere else. Every sealed hierarchy in the domain gets a mix-in naming its variants, so the
 * committed JSON carries a stable {@code "type"} discriminator that reads well in a diff and does not shift when a
 * record is renamed.
 *
 * <p>Derived accessors that happen to be getter-shaped are ignored here too, otherwise Jackson would write them out and
 * then refuse to read them back.
 */
final class Mixins {

    private Mixins() {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = AgentKind.Person.class, name = "person"),
        @Type(value = AgentKind.Organisation.class, name = "organisation")
    })
    abstract static class AgentKindMixin {}

    abstract static class AgentMixin {
        @JsonIgnore
        abstract Stream<String> names();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = Role.Author.class, name = "author"),
        @Type(value = Role.Translator.class, name = "translator"),
        @Type(value = Role.Editor.class, name = "editor"),
        @Type(value = Role.Illustrator.class, name = "illustrator"),
        @Type(value = Role.Narrator.class, name = "narrator"),
        @Type(value = Role.Publisher.class, name = "publisher"),
        @Type(value = Role.Other.class, name = "other")
    })
    abstract static class RoleMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = BibliographicDate.Exact.class, name = "exact"),
        @Type(value = BibliographicDate.Year.class, name = "year"),
        @Type(value = BibliographicDate.Circa.class, name = "circa"),
        @Type(value = BibliographicDate.Between.class, name = "between"),
        @Type(value = BibliographicDate.Unknown.class, name = "unknown")
    })
    abstract static class BibliographicDateMixin {
        @JsonIgnore
        abstract Optional<Integer> sortYear();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = ExpressionKind.Original.class, name = "original"),
        @Type(value = ExpressionKind.Translation.class, name = "translation"),
        @Type(value = ExpressionKind.Revision.class, name = "revision"),
        @Type(value = ExpressionKind.Abridgement.class, name = "abridgement"),
        @Type(value = ExpressionKind.Adaptation.class, name = "adaptation"),
        @Type(value = ExpressionKind.Narration.class, name = "narration")
    })
    abstract static class ExpressionKindMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = WorkForm.Novel.class, name = "novel"),
        @Type(value = WorkForm.Novella.class, name = "novella"),
        @Type(value = WorkForm.ShortStories.class, name = "short-stories"),
        @Type(value = WorkForm.Poetry.class, name = "poetry"),
        @Type(value = WorkForm.Drama.class, name = "drama"),
        @Type(value = WorkForm.Essay.class, name = "essay"),
        @Type(value = WorkForm.Nonfiction.class, name = "nonfiction"),
        @Type(value = WorkForm.Reference.class, name = "reference"),
        @Type(value = WorkForm.Comics.class, name = "comics"),
        @Type(value = WorkForm.Other.class, name = "other")
    })
    abstract static class WorkFormMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = Carrier.Hardcover.class, name = "hardcover"),
        @Type(value = Carrier.Paperback.class, name = "paperback"),
        @Type(value = Carrier.MassMarket.class, name = "mass-market"),
        @Type(value = Carrier.Ebook.class, name = "ebook"),
        @Type(value = Carrier.Audiobook.class, name = "audiobook"),
        @Type(value = Carrier.Other.class, name = "other")
    })
    abstract static class CarrierMixin {
        @JsonIgnore
        abstract boolean physical();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = Identifier.Isbn13.class, name = "isbn13"),
        @Type(value = Identifier.Isbn10.class, name = "isbn10"),
        @Type(value = Identifier.Asin.class, name = "asin"),
        @Type(value = Identifier.Custom.class, name = "custom"),
        @Type(value = Identifier.None.class, name = "none")
    })
    abstract static class IdentifierMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = Extent.Pages.class, name = "pages"),
        @Type(value = Extent.Volumes.class, name = "volumes"),
        @Type(value = Extent.Playtime.class, name = "playtime"),
        @Type(value = Extent.Unspecified.class, name = "unspecified")
    })
    abstract static class ExtentMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = Acquisition.Purchased.class, name = "purchased"),
        @Type(value = Acquisition.Gift.class, name = "gift"),
        @Type(value = Acquisition.Inherited.class, name = "inherited"),
        @Type(value = Acquisition.Borrowed.class, name = "borrowed"),
        @Type(value = Acquisition.Unrecorded.class, name = "unrecorded")
    })
    abstract static class AcquisitionMixin {
        @JsonIgnore
        abstract boolean owned();

        @JsonIgnore
        abstract Optional<LocalDate> on();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = Location.Shelf.class, name = "shelf"),
        @Type(value = Location.Box.class, name = "box"),
        @Type(value = Location.LentTo.class, name = "lent-to"),
        @Type(value = Location.Device.class, name = "device"),
        @Type(value = Location.Missing.class, name = "missing")
    })
    abstract static class LocationMixin {
        @JsonIgnore
        abstract boolean athand();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
        @Type(value = ReadingProgress.Unread.class, name = "unread"),
        @Type(value = ReadingProgress.Reading.class, name = "reading"),
        @Type(value = ReadingProgress.Finished.class, name = "finished"),
        @Type(value = ReadingProgress.Abandoned.class, name = "abandoned")
    })
    abstract static class ReadingProgressMixin {}

    abstract static class ManifestationMixin {
        @JsonIgnore
        abstract boolean isCompilation();
    }

    abstract static class WorkMixin {
        @JsonIgnore
        abstract String byline();
    }

    abstract static class ExpressionMixin {
        @JsonIgnore
        abstract String describe();
    }

    static void applyTo(ObjectMapper mapper) {
        mapper.addMixIn(Agent.class, AgentMixin.class);
        mapper.addMixIn(AgentKind.class, AgentKindMixin.class);
        mapper.addMixIn(Role.class, RoleMixin.class);
        mapper.addMixIn(BibliographicDate.class, BibliographicDateMixin.class);
        mapper.addMixIn(ExpressionKind.class, ExpressionKindMixin.class);
        mapper.addMixIn(WorkForm.class, WorkFormMixin.class);
        mapper.addMixIn(Carrier.class, CarrierMixin.class);
        mapper.addMixIn(Identifier.class, IdentifierMixin.class);
        mapper.addMixIn(Extent.class, ExtentMixin.class);
        mapper.addMixIn(Acquisition.class, AcquisitionMixin.class);
        mapper.addMixIn(Location.class, LocationMixin.class);
        mapper.addMixIn(ReadingProgress.class, ReadingProgressMixin.class);
        mapper.addMixIn(Manifestation.class, ManifestationMixin.class);
        mapper.addMixIn(Work.class, WorkMixin.class);
        mapper.addMixIn(Expression.class, ExpressionMixin.class);
    }
}
