$(function() {

    //Accessibility
    var errorSummary =  $('#error-summary-display'),
    $input = $('input:text');

    //Error summary focus
    if (errorSummary){ errorSummary.focus() }


    //Trim inputs and Capitalize postode
    $('[type="submit"]').click(function(){
        $input.each( function(){
            if($(this).val() && $(this).attr('data-uppercase') === 'true' ){
                $(this).val($(this).val().toUpperCase().replace(/\s\s+/g, ' ').trim())
            }else{
                $(this).val($(this).val().trim())
            }
        });
    });

    //Add aria-hidden to hidden inputs
    $('[type="hidden"]').attr("aria-hidden", true);

    var showHideContent = new GOVUK.ShowHideContent();
    showHideContent.init();

    var selectEl = document.querySelector('#amls-auto-complete');
      if(selectEl){
          accessibleAutocomplete.enhanceSelectElement({
            autoselect: true,
            defaultValue: selectEl.options[selectEl.options.selectedIndex].innerHTML,
            minLength: 2,
            selectElement: selectEl
          })
      }

      function findCountry(country) {
          return  country == $("#amls-auto-complete").val();
      }

    //custom handler for AMLS auto-complete dropdown
    $('#amls-auto-complete').change(function(){
        var changedValue = $(this).val();
        var array = [];

        $('.autocomplete__menu li').each(function(){
            array.push($(this).text())
        });

        if(array == "No results found"){
            $('#amls-auto-complete-select').append('<option id="notFound" value="NOTFOUND">No results found</option>');
            $('#amls-auto-complete-select').val('NOTFOUND').attr("selected", "selected");

        }else if(array == ""){
            $('#amls-auto-complete-select').val('').attr("selected", "selected");
        }

    });

     $('.form-date label.form-field--error').each(function () {

        $(this).closest('div').addClass('form-field--error');
        var $relocate = $(this).closest('fieldset').find('legend');
        $(this).find('.error-notification').appendTo($relocate)

     });

    // by default the dropForm will be hidden so we we need this to make the form visible after loaded
    $('#dropForm').css('visibility', 'visible');

    $('a[role=button]').keyup(function(e) {
        // get the target element
        var target = e.target;

        // if the element has a role=’button’ and the pressed key is a space, we’ll simulate a click
        if (e.keyCode === 32) {
            e.preventDefault();
            // trigger the target’s click event
            target.click()
        }
    });

    // ------------------------------------
    // Introduce direct skip link control, to work around voiceover failing of hash links
    // https://bugs.webkit.org/show_bug.cgi?id=179011
    // https://axesslab.com/skip-links/
    // ------------------------------------
    $('.skiplink').click(function(e) {
        e.preventDefault();
        $(':header:first').attr('tabindex', '-1').focus();
    });
});
