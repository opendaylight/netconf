//constructs a pagination
var loadPagination= function(url, mountIndex, mountPath, dom_id) {
    $.ajax( {
        url: url + mountIndex + "?totalPages",
        datatype: 'jsonp',
        success: function( strData ){
            var totalPages = strData;
            $(function() {
                $('#light-pagination').pagination({
                    pages: totalPages,
                    cssStyle: 'light-theme',
                    onPageClick: function(pageNumber, event) {
                        loadSwagger(url + mountIndex + "?pageNum=" + pageNumber,
                            dom_id, mountIndex, mountPath);
                    }
                });
            });
        }
    } );
}