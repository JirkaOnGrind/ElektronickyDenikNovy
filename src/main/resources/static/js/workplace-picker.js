(function () {
    function initializePicker(picker) {
        const select = picker.querySelector('[data-workplace-select]');
        const newWorkplaceContainer = picker.querySelector('[data-workplace-new]');
        const newWorkplaceInput = picker.querySelector('[data-workplace-new-input]');

        if (!select || !newWorkplaceContainer || !newWorkplaceInput) {
            return;
        }

        function updateNewWorkplaceVisibility(shouldFocus) {
            const isCreating = select.value === '__NEW__';
            newWorkplaceContainer.classList.toggle('hidden', !isCreating);
            newWorkplaceInput.required = isCreating;

            if (!isCreating) {
                newWorkplaceInput.value = '';
            } else if (shouldFocus) {
                newWorkplaceInput.focus();
            }
        }

        select.addEventListener('change', function () {
            updateNewWorkplaceVisibility(true);
        });
        updateNewWorkplaceVisibility(false);
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-workplace-picker]').forEach(initializePicker);
    });
})();
