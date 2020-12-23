/* https://stackoverflow.com/a/45071478 */
/* doesn't work yet */
const items = document.querySelectorAll("span");

items.forEach(item => {
  item.onclick = function() {
    item.select();
    item.setSelectionRange(0, 1048576);
    document.execCommand("copy");
  };
});

items.forEach(item => {
  item.addEventListener("copy", function(event) {
    event.preventDefault();
    event.clipboardData.setData("text/plain", span.textContent);
    console.log(event.clipboardData.getData("text"));
  })
});
