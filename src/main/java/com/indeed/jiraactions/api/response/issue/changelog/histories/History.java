package com.indeed.jiraactions.api.response.issue.changelog.histories;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.indeed.jiraactions.JiraActionsUtil;
import com.indeed.jiraactions.api.response.issue.User;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author soono
 */

@JsonIgnoreProperties(ignoreUnknown=true)
public class History {
    public User author;
    public DateTime created;
    public Item[] items;

    @JsonProperty("created")
    public void setCreate(final String created) {
        this.created = JiraActionsUtil.parseDateTime(created);
    }

    public String getChangedFields() {
        final Set<String> fieldsChanged = new HashSet<>();
        for (final Item item : items) {
            fieldsChanged.add(item.field);
        }
        return Joiner.on(" ").join(fieldsChanged.iterator());
    }

    public boolean itemExist(final String field) {
        return itemExist(field, false);
    }

    public boolean itemExist(final String field, final boolean acceptCustom) {
        for (final Item item : items) {
            if (Objects.equals(item.field, field) && (acceptCustom || !item.customField)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public Item getItem(final String field, final boolean acceptCustom) {
        for (final Item item : items) {
            if (Objects.equals(item.field, field) && (acceptCustom || !item.customField)) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    public Item getItem(final boolean acceptCustom, final String... fields) {
        Item bestItem = null;
        for(final Item item : items) {
            for(final String field : fields) {
                if (Objects.equals(item.field, field) && (acceptCustom || !item.customField)) {
                    if(StringUtils.isNotEmpty(item.toString)) {
                        return item;
                    } else {
                        bestItem = item;
                        break;
                    }
                }
            }
        }
        return bestItem;
    }

    public List<Item> getAllItems(@Nonnull final String field) {
        return Arrays.stream(items).filter(i -> field.equals(i.field)).collect(Collectors.toList());
    }

    public String getItemLastValue(final String field) {
        return getItemLastValue(field, false);
    }

    public String getItemLastValue(final String field, final boolean acceptCustom) {
        final Item item = getItem(field, acceptCustom);
        if (item == null || item.toString == null) {
            return "";
        }

        return item.toString;
    }

    public String getItemLastValueKey(final String field) {
        return getItemLastValueKey(field, false);
    }

    public String getItemLastValueKey(final String field, final boolean acceptCustom) {
        final Item item = getItem(field, acceptCustom);
        if (item == null || item.to == null) {
            return "";
        }

        return item.to;
    }
}
