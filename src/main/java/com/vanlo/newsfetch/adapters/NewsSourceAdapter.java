package com.vanlo.newsfetch.adapters;

import com.vanlo.newsfetch.domain.SourceConfig;
import com.vanlo.newsfetch.domain.SourceType;

public interface NewsSourceAdapter {

    boolean supports(SourceType sourceType);

    SourceFetchResult fetch(SourceConfig sourceConfig);
}
